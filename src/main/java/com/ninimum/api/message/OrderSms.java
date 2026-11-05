package com.ninimum.api.message;

import com.ninimum.api.common.PhoneNumbers;
import com.ninimum.api.message.service.impl.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Objects;

/**
 * Customer SMS through Eskiz when an order goes on the way and when it is delivered. Reads the order status
 * history, so every path (courier app, taxi, web admin) is covered. Each text is off until Eskiz approves it.
 */
@Slf4j
@Service
public class OrderSms {
    public interface Sender { boolean send(String phone, String text) throws Exception; }

    // Eskiz-approved wording (ID 98956). The straight apostrophe keeps it to 1 SMS; a curly ‘ would make it 2.
    static final String ON_THE_WAY = "Ninimum: %s raqamli buyurtmangiz yo'lga chiqdi. Kuryer tez orada yetkazib beradi.";
    static final String TAXI = "Ninimum: %s raqamli buyurtmangiz taksi orqali yo‘lga chiqdi. Haydovchi: %s, mashina: %s.";
    static final String DELIVERED = "Ninimum: %s raqamli buyurtmangiz yetkazildi. Xaridingiz uchun rahmat!";

    private final JdbcTemplate jdbc;
    private final Sender sender;
    @Value("${sms.order-on-way-enabled:true}") private boolean onWayEnabled;
    @Value("${sms.order-taxi-enabled:false}") private boolean taxiEnabled;
    @Value("${sms.order-delivered-enabled:false}") private boolean deliveredEnabled;
    private boolean schemaWarning;

    @Autowired
    public OrderSms(JdbcTemplate jdbc, MessageService messages) { this(jdbc, messages::sendSms); }
    public OrderSms(JdbcTemplate jdbc, Sender sender) { this.jdbc = jdbc; this.sender = sender; }
    void enable(boolean onWay, boolean taxi, boolean delivered) { onWayEnabled = onWay; taxiEnabled = taxi; deliveredEnabled = delivered; }

    // Every 15 s: an order delivered right after it went on the way still gets its "on the way" SMS.
    @Scheduled(fixedDelayString = "${sms.orders.interval-ms:15000}", initialDelayString = "${sms.orders.initial-delay-ms:60000}")
    public synchronized void tick() {
        if (!onWayEnabled && !taxiEnabled && !deliveredEnabled) return;
        try { scan(); schemaWarning = false; }
        catch (DataAccessException ex) { if (!schemaWarning) log.warn("Order SMS unavailable. Check the order_sms migration."); schemaWarning = true; }
    }

    // Only recent changes: turning a text on later doesn't message old orders.
    private static final String RECENT = "SELECT o.id,o.delivery_method,u.phone_number,t.driver_phone,t.car_number FROM order_status_history h " +
            "JOIN orders o ON o.id=h.order_id JOIN users u ON u.id=o.user_id LEFT JOIN taxi_deliveries t ON t.order_id=o.id " +
            "WHERE h.to_status=? AND h.changed_at>=UTC_TIMESTAMP()-INTERVAL 30 MINUTE AND o.status=? " +
            "AND NOT EXISTS(SELECT 1 FROM order_sms s WHERE s.order_id=o.id AND s.kind=?) GROUP BY o.id";

    void scan() {
        if (onWayEnabled || taxiEnabled) for (var row : jdbc.queryForList(RECENT, "ON_THE_WAY", "ON_THE_WAY", "ON_THE_WAY")) {
            boolean taxi = "TAXI".equals(row.get("delivery_method"));
            if (taxi ? !taxiEnabled || row.get("driver_phone") == null : !onWayEnabled) continue;
            String text = taxi ? String.format(TAXI, number(row), row.get("driver_phone"), row.get("car_number")) : String.format(ON_THE_WAY, number(row));
            send(row, "ON_THE_WAY", text);
        }
        if (deliveredEnabled) for (var row : jdbc.queryForList(RECENT, "DELIVERED", "DELIVERED", "DELIVERED"))
            send(row, "DELIVERED", String.format(DELIVERED, number(row)));
    }

    private static String number(Map<String, Object> row) { return String.format("#%06d", ((Number) row.get("id")).longValue()); }

    /** Claims the order first, so a slow or failed send is never repeated. */
    private void send(Map<String, Object> row, String kind, String text) {
        String phone = Objects.toString(row.get("phone_number"), "");
        // Deleted and merged accounts keep a technical number ("deleted-2"), which is not a phone.
        if (PhoneNumbers.normalize(phone) == null) return;
        long order = ((Number) row.get("id")).longValue();
        if (jdbc.update("INSERT IGNORE INTO order_sms(order_id,kind,phone,text,status,created_at) VALUES(?,?,?,?,'SENDING',UTC_TIMESTAMP())", order, kind, phone, text) == 0) return;
        String status, error = null;
        try { status = sender.send(phone, text) ? "SENT" : "FAILED"; }
        catch (Exception ex) { status = "FAILED"; error = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName()); if (error.length() > 300) error = error.substring(0, 300); }
        jdbc.update("UPDATE order_sms SET status=?,error=? WHERE order_id=? AND kind=?", status, error, order, kind);
        if (!"SENT".equals(status)) log.warn("Order SMS {} for order {} failed: {}", kind, order, error);
    }
}
