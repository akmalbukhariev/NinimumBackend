package com.ninimum.api.deliveryapp;

import com.ninimum.api.deliveryapp.tracking.CourierTracking;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Courier GPS V1b on the real schema (reports_test + 20261111_delivery_proof.sql + 20261111_courier_tracking.sql). */
@EnabledIfSystemProperty(named = "reports.test.jdbc", matches = ".*")
class CourierTrackingIntegrationTest {
    JdbcTemplate jdbc; CourierTracking tracking; SingleConnectionDataSource ds;

    @BeforeEach void setup() {
        String url = System.getProperty("reports.test.jdbc");
        if (!url.matches("jdbc:mariadb://127\\.0\\.0\\.1:[0-9]+/reports_test")) throw new IllegalArgumentException("Dedicated local reports_test only");
        ds = new SingleConnectionDataSource(url, "root", "", true);
        jdbc = new JdbcTemplate(ds);
        for (String f : List.of("20261111_delivery_proof.sql", "20261111_courier_tracking.sql"))
            new ResourceDatabasePopulator(new FileSystemResource("database/migrations/" + f)).execute(ds);
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.execute("SET SESSION sql_mode=''");
        for (String t : List.of("courier_locations", "courier_last_location", "delivery_job_tracking", "delivery_jobs", "order_status_history", "orders", "delivery_app_workers",
                "order_problem_alerts", "order_problem_events", "order_timer_settings", "delivery_confirmations"))
            jdbc.execute("DELETE FROM " + t);
        jdbc.update("INSERT INTO delivery_app_workers(id,worker_code,full_name,password_hash,status,is_online) VALUES(5,'DEL5','Kuryer 5','x','ACTIVE',1),(6,'DEL6','Old','x','INACTIVE',0)");
        jdbc.update("INSERT INTO orders(id,user_id,order_number,status,payment_status,subtotal_price,total_price,delivery_latitude,delivery_longitude) VALUES(1,1,'C1','ON_THE_WAY','PAID',5000,5000,39.06,66.83)");
        jdbc.update("INSERT INTO delivery_jobs(id,order_id,delivery_worker_id,customer_name,customer_phone,delivery_address,status) VALUES(11,1,5,'A','1','x','ON_THE_WAY')");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        tracking = new CourierTracking(jdbc);
    }
    @AfterEach void close() { ds.destroy(); }

    Map<String, Object> point(Instant at, double lat, double lon) {
        return new HashMap<>(Map.of("recorded_at", at.toEpochMilli(), "latitude", lat, "longitude", lon, "accuracy", 8.0, "battery", 77, "mock", false));
    }

    @Test void phoneBatchesGoToTheMapAndRoutes() {
        Instant now = Instant.now().minusSeconds(60);
        var batch = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 5; i++) batch.add(point(now.minusSeconds(60L * (4 - i)), 39.05 + i * 0.001, 66.83));
        batch.add(point(now.minus(Duration.ofDays(5)), 39.0, 66.8));                 // too old: skipped
        batch.add(new HashMap<>(Map.of("recorded_at", now.toEpochMilli(), "latitude", 0, "longitude", 0)));  // no fix: skipped
        assertEquals(5, tracking.save("DEL5", batch));
        assertEquals(0, tracking.save("DEL5", batch), "a resent batch is not stored twice");
        assertEquals(0, tracking.save("DEL6", batch), "inactive courier");
        @SuppressWarnings("unchecked") var couriers = (List<Map<String, Object>>) tracking.live().get("couriers");
        var c = couriers.stream().filter(x -> ((Number) x.get("id")).longValue() == 5).findFirst().orElseThrow();
        assertEquals(39.054, ((Number) c.get("latitude")).doubleValue(), 1e-9, "last point is the newest");
        assertEquals(77, ((Number) c.get("battery_pct")).intValue());
        assertEquals(1, ((List<?>) c.get("orders")).size(), "the order the courier carries");
        jdbc.update("INSERT INTO delivery_job_tracking(delivery_job_id,delivery_worker_id,status,message,created_at) VALUES(11,5,'ON_THE_WAY','x',UTC_TIMESTAMP()-INTERVAL 10 MINUTE)");
        var route = tracking.order(1);
        assertEquals(5, ((List<?>) route.get("points")).size(), "points since the courier started the order");
        double km = ((Number) ((Map<?, ?>) route.get("summary")).get("km")).doubleValue();
        assertTrue(km > 0.3 && km < 0.6, "about 0.44 km: " + km);
        var day = tracking.day(5, LocalDate.now(ZoneId.of("Asia/Tashkent")));
        assertTrue(((List<?>) day.get("points")).size() >= 1);
    }

    List<String> alerts() {
        return jdbc.queryForList("SELECT CONCAT(entity_id,':',kind) FROM order_problem_alerts WHERE resolved_at IS NULL AND kind LIKE 'COURIER_%' ORDER BY kind", String.class);
    }

    @Test void longStopNoSignalAndOutOfArea() {
        jdbc.update("INSERT INTO order_timer_settings(id,working_days,working_start,working_end) VALUES(1,127,'00:00','24:00')");
        jdbc.update("UPDATE delivery_jobs SET on_the_way_at=UTC_TIMESTAMP()-INTERVAL 40 MINUTE WHERE id=11");
        var timers = new com.ninimum.api.ordersafety.OrderTimersService(jdbc, new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds));
        Instant now = Instant.now();
        // Standing 2 km from the customer (39.06,66.83) for 20 minutes.
        var batch = new ArrayList<Map<String, Object>>();
        for (int i = 20; i >= 0; i -= 2) batch.add(point(now.minusSeconds(60L * i), 39.078 + (i % 4) * 0.0001, 66.83));
        tracking.save("DEL5", batch);
        timers.scan(now);
        assertEquals(List.of("1:COURIER_LONG_STOP"), alerts());
        // He drives on: the stop closes. Then the phone goes silent for 12 minutes.
        tracking.save("DEL5", List.of(point(now.plusSeconds(30), 39.065, 66.83)));
        timers.scan(now.plusSeconds(40));
        assertEquals(List.of(), alerts());
        timers.scan(now.plus(Duration.ofMinutes(13)));
        assertEquals(List.of("1:COURIER_NO_SIGNAL"), alerts());
        // A position 40 km away while carrying the order.
        tracking.save("DEL5", List.of(point(now.plus(Duration.ofMinutes(4)), 39.42, 66.83)));   // the server accepts up to 5 min ahead
        timers.scan(now.plus(Duration.ofMinutes(4)).plusSeconds(10));
        assertEquals(List.of("1:COURIER_OUT_OF_AREA"), alerts());
    }
}
