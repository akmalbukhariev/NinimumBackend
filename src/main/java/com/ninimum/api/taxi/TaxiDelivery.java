package com.ninimum.api.taxi;

import com.ninimum.api.ordersafety.WorkingHours;
import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Fast delivery by an outside taxi. The customer chooses it at checkout (working hours only) and pays the driver
 * directly; Ninimum's delivery fee stays 0. Couriers never see these orders (no delivery job is created).
 * After the warehouse marks the order ready, an operator books a taxi here: READY → ON_THE_WAY (stock is handed over),
 * then DELIVERED, or RETURNING when the taxi could not deliver (normal return and refund flow).
 */
@Service
public class TaxiDelivery {
    public static final String TAXI = "TAXI", COURIER = "COURIER";
    private final JdbcTemplate jdbc;
    // Optional so tests that build the service with jdbc only still work.
    @Autowired(required = false) private WarehouseService warehouse;
    public TaxiDelivery(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public static String method(String requested) {
        return TAXI.equalsIgnoreCase(Objects.toString(requested, "").trim()) ? TAXI : COURIER;
    }

    /** Taxi delivery is offered only inside the working hours set in Taymer sozlamalari. */
    public boolean available() {
        try {
            var settings = jdbc.queryForMap("SELECT working_days,working_start,working_end FROM order_timer_settings WHERE id=1");
            Instant now = Instant.now();
            return new WorkingHours(settings).minutes(now, now.plusSeconds(60)) > 0;
        } catch (DataAccessException | IllegalArgumentException ex) { return false; }
    }

    /** Book (or correct) the taxi. A READY order goes ON_THE_WAY; for an order already on the way only the details change. */
    @Transactional
    public void book(long orderId, Map<String,Object> body, String actor) {
        var order = lockTaxiOrder(orderId);
        String status = String.valueOf(order.get("status"));
        if (!"PAID".equals(order.get("payment_status")) || !Set.of("READY", "ON_THE_WAY").contains(status)) throw new WarehouseException("TAXI_NOT_READY");
        String company = text(body.get("company"), 80, true), phone = text(body.get("driver_phone"), 40, true), car = text(body.get("car_number"), 30, true);
        String name = text(body.get("driver_name"), 120, false), note = text(body.get("note"), 500, false);
        BigDecimal fare = null;
        Object rawFare = body.get("quoted_fare");
        if (rawFare != null && !String.valueOf(rawFare).isBlank()) {
            try { fare = new BigDecimal(String.valueOf(rawFare)); } catch (NumberFormatException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
            if (fare.signum() < 0 || fare.compareTo(new BigDecimal("10000000")) > 0) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        }
        jdbc.update("INSERT INTO taxi_deliveries(order_id,company,driver_name,driver_phone,car_number,quoted_fare,note,booked_by,booked_at,updated_at) VALUES(?,?,?,?,?,?,?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP()) " +
                "ON DUPLICATE KEY UPDATE company=VALUES(company),driver_name=VALUES(driver_name),driver_phone=VALUES(driver_phone),car_number=VALUES(car_number),quoted_fare=VALUES(quoted_fare),note=VALUES(note),updated_at=UTC_TIMESTAMP()",
                orderId, company, name, phone, car, fare, note, actor);
        if ("READY".equals(status)) {
            // Same handover as a courier starting the delivery: the packed goods leave the warehouse.
            if (warehouse != null) warehouse.issueOrderStock(orderId);
            if (jdbc.update("UPDATE orders SET status='ON_THE_WAY',status_source='TAXI',status_actor=?,updated_at=NOW() WHERE id=? AND status='READY'", actor, orderId) != 1)
                throw new WarehouseException("TAXI_NOT_READY");
        }
    }

    @Transactional
    public void delivered(long orderId, String actor) {
        var order = lockTaxiOrder(orderId);
        if (!"ON_THE_WAY".equals(order.get("status"))) throw new WarehouseException("TAXI_NOT_ON_THE_WAY");
        jdbc.update("UPDATE orders SET status='DELIVERED',delivered_at=NOW(),status_source='TAXI',status_actor=?,updated_at=NOW() WHERE id=? AND status='ON_THE_WAY'", actor, orderId);
        jdbc.update("UPDATE taxi_deliveries SET delivered_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE order_id=?", orderId);
    }

    /** The taxi could not deliver (customer refused, not reachable…): the goods come back and the order is refunded as a return. */
    @Transactional
    public void failed(long orderId, String reason, String actor) {
        var order = lockTaxiOrder(orderId);
        if (!"ON_THE_WAY".equals(order.get("status"))) throw new WarehouseException("TAXI_NOT_ON_THE_WAY");
        String why = text(reason, 500, true);
        jdbc.update("UPDATE orders SET status='RETURNING',status_source='TAXI',status_actor=?,status_reason=?,cancel_reason=?,updated_at=NOW() WHERE id=? AND status='ON_THE_WAY'", actor, why, why, orderId);
        jdbc.update("UPDATE taxi_deliveries SET failed_reason=?,updated_at=UTC_TIMESTAMP() WHERE order_id=?", why, orderId);
    }

    /** The customer pressed "Mahsulotni oldim": their own taxi order on the way becomes DELIVERED. */
    @Transactional
    public void customerReceived(long orderId, long userId) throws Exception {
        customerTaxiOrderOnTheWay(orderId, userId);
        jdbc.update("UPDATE orders SET status='DELIVERED',delivered_at=NOW(),status_source='CUSTOMER',status_actor=?,updated_at=NOW() WHERE id=? AND status='ON_THE_WAY'", "CUSTOMER:" + userId, orderId);
        jdbc.update("UPDATE taxi_deliveries SET delivered_at=UTC_TIMESTAMP(),confirmed_by_customer=1,updated_at=UTC_TIMESTAMP() WHERE order_id=?", orderId);
    }

    /** The customer pressed "Muammo bor": the reason is stored and Muammolar/Telegram alert the operator. The order itself does not change. */
    @Transactional
    public void customerProblem(long orderId, long userId, String reason) throws Exception {
        customerTaxiOrderOnTheWay(orderId, userId);
        String why = Objects.toString(reason, "").trim();
        if (why.isEmpty() || why.length() > 500) throw new Exception("A reason of 1–500 characters is required");
        jdbc.update("UPDATE taxi_deliveries SET customer_problem=?,customer_problem_at=UTC_TIMESTAMP(),updated_at=UTC_TIMESTAMP() WHERE order_id=?", why, orderId);
    }

    private void customerTaxiOrderOnTheWay(long orderId, long userId) throws Exception {
        var rows = jdbc.queryForList("SELECT status,delivery_method FROM orders WHERE id=? AND user_id=? FOR UPDATE", orderId, userId);
        if (rows.isEmpty() || !TAXI.equals(rows.get(0).get("delivery_method"))) throw new Exception("Order not found");
        if (!"ON_THE_WAY".equals(rows.get(0).get("status"))) throw new Exception("This order is not on the way");
    }

    /** Taxi details of an order, or null. */
    public Map<String,Object> details(long orderId) {
        try {
            var rows = jdbc.queryForList("SELECT * FROM taxi_deliveries WHERE order_id=?", orderId);
            if (rows.isEmpty()) return null;
            var row = new LinkedHashMap<String,Object>(rows.get(0));
            for (String key : List.of("booked_at", "updated_at", "delivered_at", "customer_problem_at"))
                if (row.get(key) != null) row.put(key, row.get(key).toString().replace(' ', 'T').replaceAll("\\.\\d+$", "") + "Z");
            rows = List.of(row);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ex) { return null; }
    }

    private Map<String,Object> lockTaxiOrder(long orderId) {
        var rows = jdbc.queryForList("SELECT status,payment_status,delivery_method FROM orders WHERE id=? FOR UPDATE", orderId);
        if (rows.isEmpty()) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        if (!TAXI.equals(rows.get(0).get("delivery_method"))) throw new WarehouseException("TAXI_NOT_TAXI_ORDER");
        return rows.get(0);
    }

    private static String text(Object value, int max, boolean required) {
        String s = value == null ? "" : String.valueOf(value).trim();
        if (s.length() > max || (required && s.isEmpty())) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        return s.isEmpty() ? null : s;
    }
}
