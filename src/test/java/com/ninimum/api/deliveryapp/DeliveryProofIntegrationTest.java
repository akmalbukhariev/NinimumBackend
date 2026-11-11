package com.ninimum.api.deliveryapp;

import com.ninimum.api.deliveryapp.service.DeliveryProof;
import com.ninimum.api.ordersafety.OrderTimersService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Courier GPS V1a on the real schema (reports_test: audit schema + all migrations, plus 20261111_delivery_proof.sql). */
@EnabledIfSystemProperty(named = "reports.test.jdbc", matches = ".*")
class DeliveryProofIntegrationTest {
    JdbcTemplate jdbc; DeliveryProof proof; SingleConnectionDataSource ds;
    // Customer's map point in Shahrisabz; 1 km north is about +0.009 degrees.
    static final double LAT = 39.0570, LON = 66.8340;

    @BeforeEach void setup() {
        String url = System.getProperty("reports.test.jdbc");
        if (!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test")) throw new IllegalArgumentException("Dedicated local reports_test only");
        ds = new SingleConnectionDataSource(url, "root", "", true);
        jdbc = new JdbcTemplate(ds);
        new ResourceDatabasePopulator(new FileSystemResource("database/migrations/20261111_delivery_proof.sql")).execute(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.execute("SET SESSION sql_mode=''"); // test rows fill only the columns this test needs
        for (String t : List.of("delivery_confirmations", "delivery_proof_photos", "delivery_job_tracking", "delivery_jobs", "order_status_history", "orders",
                "order_problem_alerts", "order_problem_events", "order_timer_settings", "delivery_app_workers"))
            jdbc.execute("DELETE FROM " + t);
        jdbc.update("DELETE FROM users WHERE id IN (1,2)");
        jdbc.update("INSERT INTO users(id,phone_number,first_name,location_latitude,location_longitude) VALUES(1,'+998900000001','A',?,?),(2,'+998900000002','B',NULL,NULL)", LAT, LON);
        jdbc.update("INSERT INTO delivery_app_workers(id,worker_code,full_name,password_hash) VALUES(5,'DEL5','Kuryer 5','x')");
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,delivery_method) VALUES" +
                "(1,1,'C1','DELIVERED','PAID',5000,5000,'COURIER'),(2,1,'C2','DELIVERED','PAID',5000,5000,'COURIER'),(3,1,'C3','ON_THE_WAY','PAID',5000,5000,'COURIER'),(4,1,'T4','DELIVERED','PAID',5000,5000,'TAXI')");
        jdbc.update("INSERT INTO delivery_jobs(id,order_id,delivery_worker_id,customer_name,customer_phone,delivery_address,status) VALUES" +
                "(11,1,5,'A','1','x','DELIVERED'),(12,2,5,'A','1','x','DELIVERED'),(13,3,5,'A','1','x','ON_THE_WAY')");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        proof = new DeliveryProof(jdbc);
    }
    @AfterEach void close() { ds.destroy(); }

    void action(long job, String status, int minutesAgo) {
        jdbc.update("INSERT INTO delivery_job_tracking(delivery_job_id,delivery_worker_id,status,message,created_at) VALUES(?,5,?,?,NOW()-INTERVAL ? MINUTE)", job, status, status, minutesAgo);
    }

    @Test void locationAndDistanceGoOnTheNewestAction() {
        action(11, "ON_THE_WAY", 3);
        action(11, "DELIVERED", 1);
        proof.stamp(11, LAT + 0.009, LON, 12.0, false, null);
        var rows = jdbc.queryForList("SELECT status,distance_m,accuracy_m,is_mock,location_error FROM delivery_job_tracking WHERE delivery_job_id=11 ORDER BY id");
        assertNull(rows.get(0).get("distance_m"), "older action untouched");
        int distance = ((Number) rows.get(1).get("distance_m")).intValue();
        assertTrue(distance > 950 && distance < 1050, "about 1 km: " + distance);
        assertEquals(12.0, ((Number) rows.get(1).get("accuracy_m")).doubleValue());
        proof.stamp(11, null, null, null, null, "permission_denied");
        assertEquals("PERMISSION_DENIED", jdbc.queryForObject("SELECT location_error FROM delivery_job_tracking WHERE delivery_job_id=11 AND status='ON_THE_WAY'", String.class),
                "the next action without a location gets the error");
    }

    @Test void customerConfirmsOnlyOwnDeliveredCourierOrder() throws Exception {
        action(11, "DELIVERED", 1); proof.stamp(11, LAT, LON, 5.0, false, null);
        assertEquals(Map.of(1L, "ASK"), proof.confirmations(List.of(1L, 2L, 3L, 4L)), "only orders delivered through the new courier app ask");
        assertEquals("Order not found", assertThrows(Exception.class, () -> proof.customerReceived(1, 2)).getMessage(), "another customer's order");
        assertEquals("Order not found", assertThrows(Exception.class, () -> proof.customerReceived(4, 1)).getMessage(), "taxi orders use their own buttons");
        assertEquals("This order is not delivered yet", assertThrows(Exception.class, () -> proof.customerReceived(3, 1)).getMessage());
        assertThrows(Exception.class, () -> proof.customerProblem(1, 1, " "), "reason required");
        proof.customerProblem(1, 1, "Bitta mahsulot yo‘q");
        assertEquals("PROBLEM", proof.confirmations(List.of(1L)).get(1L));
        proof.customerReceived(1, 1);
        assertEquals("RECEIVED", proof.confirmations(List.of(1L)).get(1L));
        proof.adminChecked(1, "owner", "Mijoz bilan gaplashildi");
        var shown = proof.forOrder(1);
        assertEquals(true, shown.get("available"));
        assertEquals("Mijoz bilan gaplashildi", ((Map<?, ?>) shown.get("confirmation")).get("check_note"));
        assertEquals(1, ((List<?>) shown.get("actions")).size());
    }

    @Test void alertsOpenAndCloseInMuammolar() {
        jdbc.update("INSERT INTO order_timer_settings(id,working_days,working_start,working_end) VALUES(1,127,'00:00','24:00')");
        var timers = new OrderTimersService(jdbc, new DataSourceTransactionManager(ds));
        action(11, "DELIVERED", 0); proof.stamp(11, LAT + 0.009, LON, 10.0, false, null);   // 1 km away
        jdbc.update("UPDATE delivery_job_tracking SET created_at=NOW()-INTERVAL 3 HOUR WHERE delivery_job_id=11"); // ... 3 hours ago
        action(12, "DELIVERED", 0); proof.stamp(12, LAT, LON, 8.0, true, null);               // at the door, but fake GPS
        timers.scan(Instant.now());
        assertEquals(List.of("1:DELIVERED_FAR", "1:NOT_CONFIRMED", "2:GPS_MOCK"), open());
        jdbc.update("INSERT INTO delivery_confirmations(order_id,received_at,updated_at) VALUES(1,UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        timers.scan(Instant.now());
        assertEquals(List.of("2:GPS_MOCK"), open(), "the customer's confirmation closes distance and no-answer alerts");
        jdbc.update("INSERT INTO delivery_confirmations(order_id,problem,problem_at,updated_at) VALUES(2,'Yo‘q',UTC_TIMESTAMP(),UTC_TIMESTAMP())");
        timers.scan(Instant.now());
        assertEquals(List.of("2:CUSTOMER_PROBLEM", "2:GPS_MOCK"), open());
        jdbc.update("UPDATE delivery_confirmations SET checked_at=UTC_TIMESTAMP(),checked_by='owner',check_note='ok' WHERE order_id=2");
        timers.scan(Instant.now());
        assertEquals(List.of(), open(), "an admin check closes everything");
    }

    List<String> open() {
        return jdbc.queryForList("SELECT CONCAT(entity_id,':',kind) FROM order_problem_alerts WHERE resolved_at IS NULL AND entity_type='ORDER' " +
                "AND kind IN ('DELIVERED_FAR','DELIVERED_NO_GPS','RETURN_FAR','GPS_MOCK','CUSTOMER_PROBLEM','NOT_CONFIRMED') ORDER BY entity_id,kind", String.class);
    }
}
