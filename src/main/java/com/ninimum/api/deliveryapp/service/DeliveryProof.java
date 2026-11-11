package com.ninimum.api.deliveryapp.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.util.*;

/**
 * Courier GPS V1a: proof that goods reached the customer.
 * - Each courier action keeps the phone's location and its distance from the customer's map point.
 * - "Yetkazildi" needs a camera photo.
 * - The customer confirms receipt or reports a problem; an admin can mark a delivery as checked.
 * Works before migration 20261111_delivery_proof.sql: location and confirmation calls do nothing, the photo is not stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryProof {
    private final JdbcTemplate jdbc;
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_BASE64 = ((MAX_BYTES + 2) / 3) * 4;
    /** A courier action farther than this (plus the phone's own accuracy, at most 100 m) from the customer is suspicious. */
    public static final int FAR_METERS = 300;

    /** Puts the phone's location on the newest courier action of this delivery that has none yet. */
    public void stamp(long jobId, Double latitude, Double longitude, Double accuracy, Boolean mock, String locationError) {
        try {
            boolean valid = latitude != null && longitude != null && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
                    && !(Math.abs(latitude) < 1e-6 && Math.abs(longitude) < 1e-6);
            String error = valid ? null : clean(locationError == null || locationError.isBlank() ? "NO_LOCATION" : locationError);
            Integer distance = null;
            if (valid) {
                var target = jdbc.queryForList("SELECT COALESCE(o.delivery_latitude,u.location_latitude) lat,COALESCE(o.delivery_longitude,u.location_longitude) lon " +
                        "FROM delivery_jobs j JOIN orders o ON o.id=j.order_id LEFT JOIN users u ON u.id=o.user_id WHERE j.id=?", jobId);
                if (!target.isEmpty() && target.get(0).get("lat") != null && target.get(0).get("lon") != null) {
                    double lat = ((Number) target.get(0).get("lat")).doubleValue(), lon = ((Number) target.get(0).get("lon")).doubleValue();
                    if (Math.abs(lat) > 1e-6 || Math.abs(lon) > 1e-6) distance = (int) Math.round(meters(latitude, longitude, lat, lon));
                }
            }
            jdbc.update("UPDATE delivery_job_tracking SET latitude=?,longitude=?,accuracy_m=?,is_mock=?,location_error=?,distance_m=? " +
                            "WHERE id=(SELECT id FROM (SELECT id FROM delivery_job_tracking WHERE delivery_job_id=? AND latitude IS NULL AND location_error IS NULL " +
                            "AND created_at>=NOW()-INTERVAL 10 MINUTE ORDER BY id DESC LIMIT 1) t)",
                    valid ? latitude : null, valid ? longitude : null, valid && accuracy != null && accuracy >= 0 ? accuracy : null,
                    Boolean.TRUE.equals(mock) ? 1 : 0, error, distance, jobId);
        } catch (DataAccessException ex) {
            log.debug("Delivery location not saved (migration 20261111 missing?): {}", ex.getMessage());
        }
    }

    /** Great-circle distance in meters. */
    public static double meters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000, p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1), dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static String clean(String value) {
        String s = value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z_]", "");
        return s.isEmpty() ? "NO_LOCATION" : s.substring(0, Math.min(40, s.length()));
    }

    /** True once the photo table exists; before the migration "Yetkazildi" works without a photo. */
    public boolean photoStorage() {
        try { jdbc.queryForList("SELECT delivery_job_id FROM delivery_proof_photos LIMIT 0"); return true; }
        catch (DataAccessException ex) { return false; }
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) throw new IllegalArgumentException("DELIVERY_PHOTO_REQUIRED");
        if (base64.length() > MAX_BASE64) throw new IllegalArgumentException("DELIVERY_PHOTO_TOO_LARGE");
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0) throw new IllegalArgumentException("DELIVERY_PHOTO_REQUIRED");
            return bytes;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("DELIVERY_PHOTO_")) throw ex;
            throw new IllegalArgumentException("DELIVERY_PHOTO_INVALID");
        }
    }
    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Checks the photo before the delivery changes, so a bad photo leaves the order on the way. */
    public byte[] requirePhoto(String base64) throws Exception {
        byte[] bytes = decode(base64);
        try { return ReturnPhotoService.normalize(bytes); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("DELIVERY_PHOTO_INVALID"); }
    }

    /** Caller has just moved its own delivery to DELIVERED in this transaction. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void savePhoto(long jobId, String actor, byte[] jpeg, String base64) throws Exception {
        String digest = sha(decode(base64));
        int saved = jdbc.update("INSERT INTO delivery_proof_photos(delivery_job_id,order_id,actor,photo_jpeg,upload_sha256,created_at) " +
                "SELECT id,order_id,?,?,?,UTC_TIMESTAMP() FROM delivery_jobs WHERE id=? AND status='DELIVERED'", actor, jpeg, digest, jobId);
        if (saved != 1) throw new IllegalStateException("Delivery changed; refresh the order");
    }

    /** A retry after a lost response: the same photo for an already delivered job is accepted once more. */
    public boolean sameDelivered(long jobId, String base64) {
        try {
            String digest = sha(decode(base64));
            return Integer.valueOf(1).equals(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_proof_photos WHERE delivery_job_id=? AND upload_sha256=?", Integer.class, jobId, digest));
        } catch (Exception ex) { return false; }
    }

    /** Everything an admin needs to judge a delivery: courier actions with places, the photo, the customer's answer. */
    public Map<String, Object> forOrder(long orderId) {
        var result = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> actions;
        try {
            actions = jdbc.queryForList("SELECT t.status,t.message,DATE_FORMAT(t.created_at,'%Y-%m-%dT%H:%i:%sZ') created_at,t.latitude,t.longitude,t.accuracy_m,t.is_mock,t.location_error,t.distance_m," +
                    "COALESCE(w.full_name,w.worker_code) courier FROM delivery_job_tracking t JOIN delivery_jobs j ON j.id=t.delivery_job_id LEFT JOIN delivery_app_workers w ON w.id=t.delivery_worker_id " +
                    "WHERE j.order_id=? ORDER BY t.id", orderId);
        } catch (DataAccessException ex) { return Map.of("available", false); }
        result.put("available", true);
        result.put("actions", actions);
        var target = jdbc.queryForList("SELECT COALESCE(o.delivery_latitude,u.location_latitude) latitude,COALESCE(o.delivery_longitude,u.location_longitude) longitude " +
                "FROM orders o LEFT JOIN users u ON u.id=o.user_id WHERE o.id=?", orderId);
        result.put("customer_point", target.isEmpty() ? null : target.get(0));
        result.put("far_meters", FAR_METERS);
        var photos = jdbc.query("SELECT photo_jpeg FROM delivery_proof_photos WHERE order_id=? ORDER BY created_at DESC LIMIT 1", (row, n) -> row.getBytes(1), orderId);
        result.put("photo", photos.isEmpty() ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photos.get(0)));
        var confirmation = jdbc.queryForList("SELECT DATE_FORMAT(received_at,'%Y-%m-%dT%H:%i:%sZ') received_at,problem,DATE_FORMAT(problem_at,'%Y-%m-%dT%H:%i:%sZ') problem_at," +
                "DATE_FORMAT(checked_at,'%Y-%m-%dT%H:%i:%sZ') checked_at,checked_by,check_note FROM delivery_confirmations WHERE order_id=?", orderId);
        result.put("confirmation", confirmation.isEmpty() ? null : confirmation.get(0));
        return result;
    }

    /** Customer side: the courier order must belong to this customer and be delivered by a courier. */
    private void customerDelivered(long orderId, long userId) throws Exception {
        var rows = jdbc.queryForList("SELECT o.status,COALESCE(o.delivery_method,'COURIER') method FROM orders o WHERE o.id=? AND o.user_id=?", orderId, userId);
        if (rows.isEmpty() || "TAXI".equals(rows.get(0).get("method"))) throw new Exception("Order not found");
        if (!"DELIVERED".equals(rows.get(0).get("status"))) throw new Exception("This order is not delivered yet");
    }

    /** "Mahsulotni oldim": closes the delivery alerts of this order. Repeating it changes nothing. */
    @Transactional
    public void customerReceived(long orderId, long userId) throws Exception {
        customerDelivered(orderId, userId);
        jdbc.update("INSERT INTO delivery_confirmations(order_id,received_at,updated_at) VALUES(?,UTC_TIMESTAMP(),UTC_TIMESTAMP()) " +
                "ON DUPLICATE KEY UPDATE received_at=COALESCE(received_at,VALUES(received_at)),updated_at=UTC_TIMESTAMP()", orderId);
    }

    /** "Muammo bor": the reason goes to Muammolar and Telegram; the order itself does not change. */
    @Transactional
    public void customerProblem(long orderId, long userId, String reason) throws Exception {
        customerDelivered(orderId, userId);
        String why = Objects.toString(reason, "").trim();
        if (why.isEmpty() || why.length() > 500) throw new Exception("A reason of 1–500 characters is required");
        jdbc.update("INSERT INTO delivery_confirmations(order_id,problem,problem_at,updated_at) VALUES(?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP()) " +
                "ON DUPLICATE KEY UPDATE problem=VALUES(problem),problem_at=VALUES(problem_at),received_at=NULL,checked_at=NULL,updated_at=UTC_TIMESTAMP()", orderId, why);
    }

    /** Admin "Tekshirildi": closes the delivery alerts of this order, with a note. */
    @Transactional
    public void adminChecked(long orderId, String actor, String note) throws Exception {
        String why = Objects.toString(note, "").trim();
        if (why.isEmpty() || why.length() > 500) throw new Exception("A note of 1–500 characters is required");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE id=?", Integer.class, orderId) != 1) throw new Exception("Order not found");
        jdbc.update("INSERT INTO delivery_confirmations(order_id,checked_at,checked_by,check_note,updated_at) VALUES(?,UTC_TIMESTAMP(),?,?,UTC_TIMESTAMP()) " +
                "ON DUPLICATE KEY UPDATE checked_at=VALUES(checked_at),checked_by=VALUES(checked_by),check_note=VALUES(check_note),updated_at=UTC_TIMESTAMP()", orderId, actor, why);
    }

    /**
     * For the customer app: ASK / RECEIVED / PROBLEM per order. Only orders whose "Yetkazildi" was pressed in the new courier app
     * (with a location or a location error), so older deliveries never show the buttons.
     */
    public Map<Long, String> confirmations(Collection<Long> orderIds) {
        var result = new HashMap<Long, String>();
        if (orderIds.isEmpty()) return result;
        try {
            String in = String.join(",", Collections.nCopies(orderIds.size(), "?"));
            for (var row : jdbc.queryForList("SELECT j.order_id,MAX(c.received_at IS NOT NULL) received,MAX(c.problem IS NOT NULL) problem FROM delivery_jobs j " +
                    "JOIN orders o ON o.id=j.order_id AND o.status='DELIVERED' JOIN delivery_job_tracking t ON t.delivery_job_id=j.id AND t.status='DELIVERED' " +
                    "AND (t.latitude IS NOT NULL OR t.location_error IS NOT NULL) LEFT JOIN delivery_confirmations c ON c.order_id=j.order_id " +
                    "WHERE j.order_id IN (" + in + ") GROUP BY j.order_id", orderIds.toArray())) {
                boolean received = yes(row.get("received")), problem = yes(row.get("problem"));
                result.put(((Number) row.get("order_id")).longValue(), received ? "RECEIVED" : problem ? "PROBLEM" : "ASK");
            }
        } catch (DataAccessException ex) { /* not migrated yet */ }
        return result;
    }
    private static boolean yes(Object v) { return Boolean.TRUE.equals(v) || (v instanceof Number n && n.intValue() != 0); }
}
