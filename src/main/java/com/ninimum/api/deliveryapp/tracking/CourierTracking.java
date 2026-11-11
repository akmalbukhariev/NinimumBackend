package com.ninimum.api.deliveryapp.tracking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/**
 * Courier GPS V1b: positions sent by the courier phone in the background, the live map and route replay.
 * Works before migration 20261111_courier_tracking.sql: uploads are ignored and the map is empty.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierTracking {
    private final JdbcTemplate jdbc;
    static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    static final int MAX_BATCH = 500, MAX_ROUTE_POINTS = 6000;

    /** Saves one batch from the phone. Bad points are skipped, duplicates ignored. Returns how many were accepted. */
    @Transactional
    public int save(String workerCode, List<Map<String, Object>> points) {
        if (points == null || points.isEmpty()) return 0;
        if (points.size() > MAX_BATCH) points = points.subList(points.size() - MAX_BATCH, points.size());
        Long worker;
        try { worker = jdbc.queryForObject("SELECT id FROM delivery_app_workers WHERE worker_code=? AND status='ACTIVE'", Long.class, workerCode); }
        catch (DataAccessException ex) { return 0; }
        Instant now = Instant.now(), oldest = now.minus(Duration.ofDays(3)), newest = now.plus(Duration.ofMinutes(5));
        int saved = 0; Map<String, Object> last = null; Instant lastAt = null;
        try {
            for (var p : points) {
                Double lat = number(p.get("latitude")), lon = number(p.get("longitude"));
                Instant at = time(p.get("recorded_at"));
                if (lat == null || lon == null || at == null || Math.abs(lat) > 90 || Math.abs(lon) > 180 || (Math.abs(lat) < 1e-6 && Math.abs(lon) < 1e-6)) continue;
                if (at.isBefore(oldest) || at.isAfter(newest)) continue;
                Double accuracy = positive(number(p.get("accuracy"))), speed = positive(number(p.get("speed")));
                Double battery = number(p.get("battery"));
                Integer batteryPct = battery == null || battery < 0 || battery > 100 ? null : (int) Math.round(battery);
                int mock = Boolean.TRUE.equals(p.get("mock")) || "true".equals(String.valueOf(p.get("mock"))) ? 1 : 0;
                saved += jdbc.update("INSERT IGNORE INTO courier_locations(worker_id,recorded_at,received_at,latitude,longitude,accuracy_m,speed_mps,battery_pct,is_mock) VALUES(?,?,UTC_TIMESTAMP(),?,?,?,?,?,?)",
                        worker, ts(at), lat, lon, accuracy, speed, batteryPct, mock);
                if (lastAt == null || at.isAfter(lastAt)) { lastAt = at; last = Map.of("lat", lat, "lon", lon, "acc", Optional.ofNullable(accuracy), "speed", Optional.ofNullable(speed), "battery", Optional.ofNullable(batteryPct), "mock", mock); }
            }
            if (last != null)
                jdbc.update("INSERT INTO courier_last_location(worker_id,recorded_at,received_at,latitude,longitude,accuracy_m,speed_mps,battery_pct,is_mock) VALUES(?,?,UTC_TIMESTAMP(),?,?,?,?,?,?) " +
                                "ON DUPLICATE KEY UPDATE latitude=IF(VALUES(recorded_at)>=recorded_at,VALUES(latitude),latitude),longitude=IF(VALUES(recorded_at)>=recorded_at,VALUES(longitude),longitude)," +
                                "accuracy_m=IF(VALUES(recorded_at)>=recorded_at,VALUES(accuracy_m),accuracy_m),speed_mps=IF(VALUES(recorded_at)>=recorded_at,VALUES(speed_mps),speed_mps)," +
                                "battery_pct=IF(VALUES(recorded_at)>=recorded_at,VALUES(battery_pct),battery_pct),is_mock=IF(VALUES(recorded_at)>=recorded_at,VALUES(is_mock),is_mock)," +
                                "received_at=UTC_TIMESTAMP(),recorded_at=GREATEST(recorded_at,VALUES(recorded_at))",
                        worker, ts(lastAt), last.get("lat"), last.get("lon"), ((Optional<?>) last.get("acc")).orElse(null),
                        ((Optional<?>) last.get("speed")).orElse(null), ((Optional<?>) last.get("battery")).orElse(null), last.get("mock"));
        } catch (DataAccessException ex) {
            log.debug("Courier locations not saved (migration 20261111_courier_tracking missing?): {}", ex.getMessage());
            return 0;
        }
        return saved;
    }

    /** Live map: every active courier with the last position, its age, battery, and the orders they carry now. */
    public Map<String, Object> live() {
        var couriers = new ArrayList<Map<String, Object>>();
        boolean available = true;
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT w.id,w.worker_code,w.full_name,w.phone_number,w.is_online,w.vehicle_type,w.vehicle_number," +
                    "DATE_FORMAT(l.recorded_at,'%Y-%m-%dT%H:%i:%sZ') recorded_at,l.latitude,l.longitude,l.accuracy_m,l.speed_mps,l.battery_pct,l.is_mock " +
                    "FROM delivery_app_workers w LEFT JOIN courier_last_location l ON l.worker_id=w.id WHERE w.status='ACTIVE' ORDER BY w.full_name");
        } catch (DataAccessException ex) {
            available = false;
            rows = jdbc.queryForList("SELECT w.id,w.worker_code,w.full_name,w.phone_number,w.is_online,w.vehicle_type,w.vehicle_number FROM delivery_app_workers w WHERE w.status='ACTIVE' ORDER BY w.full_name");
        }
        var jobs = jdbc.queryForList("SELECT j.delivery_worker_id worker_id,j.order_id,o.order_number,j.status,j.customer_name,j.delivery_address," +
                "COALESCE(o.delivery_latitude,u.location_latitude) latitude,COALESCE(o.delivery_longitude,u.location_longitude) longitude " +
                "FROM delivery_jobs j JOIN orders o ON o.id=j.order_id LEFT JOIN users u ON u.id=o.user_id WHERE j.status IN ('ACCEPTED','ON_THE_WAY') AND j.delivery_worker_id IS NOT NULL ORDER BY j.id");
        var byWorker = new HashMap<Long, List<Map<String, Object>>>();
        for (var j : jobs) byWorker.computeIfAbsent(((Number) j.get("worker_id")).longValue(), k -> new ArrayList<>()).add(j);
        for (var r : rows) {
            var c = new LinkedHashMap<String, Object>(r);
            c.put("orders", byWorker.getOrDefault(((Number) r.get("id")).longValue(), List.of()));
            couriers.add(c);
        }
        return Map.of("available", available, "couriers", couriers);
    }

    /** One courier's day (Tashkent date): all points and the courier's button presses with places. */
    public Map<String, Object> day(long workerId, LocalDate date) {
        Instant from = date.atStartOfDay(TASHKENT).toInstant(), to = date.plusDays(1).atStartOfDay(TASHKENT).toInstant();
        var result = new LinkedHashMap<String, Object>();
        result.put("points", points(workerId, from, to));
        result.put("actions", actions("t.delivery_worker_id=? AND t.created_at>=? AND t.created_at<?", workerId, ts(from), ts(to)));
        @SuppressWarnings("unchecked") var pts = (List<Map<String, Object>>) result.get("points");
        result.put("summary", summary(pts));
        return result;
    }

    /** One order's route: the courier's points from accepting the order until it was delivered or failed (or now). */
    public Map<String, Object> order(long orderId) {
        var result = new LinkedHashMap<String, Object>();
        var target = jdbc.queryForList("SELECT COALESCE(o.delivery_latitude,u.location_latitude) latitude,COALESCE(o.delivery_longitude,u.location_longitude) longitude,o.order_number " +
                "FROM orders o LEFT JOIN users u ON u.id=o.user_id WHERE o.id=?", orderId);
        result.put("customer_point", target.isEmpty() ? null : target.get(0));
        var job = jdbc.queryForList("SELECT j.id,j.delivery_worker_id,DATE_FORMAT(MIN(t.created_at),'%Y-%m-%dT%H:%i:%sZ') started," +
                "DATE_FORMAT(MAX(CASE WHEN t.status IN ('DELIVERED','FAILED') THEN t.created_at END),'%Y-%m-%dT%H:%i:%sZ') ended " +
                "FROM delivery_jobs j LEFT JOIN delivery_job_tracking t ON t.delivery_job_id=j.id WHERE j.order_id=? GROUP BY j.id ORDER BY j.id DESC LIMIT 1", orderId);
        if (job.isEmpty() || job.get(0).get("delivery_worker_id") == null || job.get(0).get("started") == null) {
            result.put("points", List.of()); result.put("actions", List.of()); return result;
        }
        long worker = ((Number) job.get(0).get("delivery_worker_id")).longValue();
        Instant from = Instant.parse(String.valueOf(job.get(0).get("started"))).minus(Duration.ofMinutes(2));
        Instant to = job.get(0).get("ended") == null ? Instant.now() : Instant.parse(String.valueOf(job.get(0).get("ended"))).plus(Duration.ofMinutes(2));
        result.put("points", points(worker, from, to));
        result.put("actions", actions("j.order_id=?", orderId));
        @SuppressWarnings("unchecked") var pts = (List<Map<String, Object>>) result.get("points");
        result.put("summary", summary(pts));
        return result;
    }

    private List<Map<String, Object>> points(long worker, Instant from, Instant to) {
        try {
            return jdbc.queryForList("SELECT DATE_FORMAT(recorded_at,'%Y-%m-%dT%H:%i:%sZ') t,latitude lat,longitude lon,accuracy_m acc,speed_mps speed,battery_pct battery,is_mock mock " +
                    "FROM courier_locations WHERE worker_id=? AND recorded_at>=? AND recorded_at<? ORDER BY recorded_at LIMIT " + MAX_ROUTE_POINTS, worker, ts(from), ts(to));
        } catch (DataAccessException ex) { return List.of(); }
    }

    private List<Map<String, Object>> actions(String where, Object... args) {
        try {
            return jdbc.queryForList("SELECT j.order_id,o.order_number,t.status,DATE_FORMAT(t.created_at,'%Y-%m-%dT%H:%i:%sZ') t,t.latitude lat,t.longitude lon,t.distance_m,t.is_mock mock " +
                    "FROM delivery_job_tracking t JOIN delivery_jobs j ON j.id=t.delivery_job_id JOIN orders o ON o.id=j.order_id WHERE " + where + " AND t.latitude IS NOT NULL ORDER BY t.created_at", args);
        } catch (DataAccessException ex) { return List.of(); }
    }

    /** Distance driven (accurate points only), first and last time, and the longest gap without points. */
    static Map<String, Object> summary(List<Map<String, Object>> points) {
        double meters = 0; long gapMinutes = 0; Map<String, Object> prev = null;
        for (var p : points) {
            Double acc = p.get("acc") == null ? null : ((Number) p.get("acc")).doubleValue();
            if (acc != null && acc > 100) continue;
            if (prev != null) {
                double d = com.ninimum.api.deliveryapp.service.DeliveryProof.meters(((Number) prev.get("lat")).doubleValue(), ((Number) prev.get("lon")).doubleValue(),
                        ((Number) p.get("lat")).doubleValue(), ((Number) p.get("lon")).doubleValue());
                if (d >= 10) meters += d;
                gapMinutes = Math.max(gapMinutes, Duration.between(Instant.parse(String.valueOf(prev.get("t"))), Instant.parse(String.valueOf(p.get("t")))).toMinutes());
            }
            prev = p;
        }
        var s = new LinkedHashMap<String, Object>();
        s.put("points", points.size());
        s.put("km", Math.round(meters / 100.0) / 10.0);
        s.put("first", points.isEmpty() ? null : points.get(0).get("t"));
        s.put("last", points.isEmpty() ? null : points.get(points.size() - 1).get("t"));
        s.put("longest_gap_minutes", gapMinutes);
        return s;
    }

    /** Keeps 90 days of points. */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        try { jdbc.update("DELETE FROM courier_locations WHERE recorded_at<UTC_TIMESTAMP()-INTERVAL 90 DAY LIMIT 200000"); }
        catch (DataAccessException ex) { /* not migrated yet */ }
    }

    /** UTC wall-clock time for SQL, like the rest of the backend. */
    static Timestamp ts(Instant value) { return Timestamp.valueOf(LocalDateTime.ofInstant(value, ZoneOffset.UTC)); }
    private static Double number(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        try { return v == null ? null : Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException ex) { return null; }
    }
    private static Double positive(Double v) { return v == null || v < 0 || v.isNaN() || v.isInfinite() ? null : v; }
    private static Instant time(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        try { return Instant.parse(String.valueOf(v)); } catch (Exception ex) { return null; }
    }
}
