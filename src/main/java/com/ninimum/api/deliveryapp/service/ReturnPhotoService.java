package com.ninimum.api.deliveryapp.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** Return evidence is available only through authenticated order/detail APIs, never public files. */
@Service
@RequiredArgsConstructor
public class ReturnPhotoService {
    private final JdbcTemplate jdbc;
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_BASE64 = ((MAX_BYTES + 2) / 3) * 4;

    private byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) throw new IllegalArgumentException("RETURN_PHOTO_REQUIRED");
        if (base64.length() > MAX_BASE64) throw new IllegalArgumentException("RETURN_PHOTO_TOO_LARGE");
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0) throw new IllegalArgumentException("RETURN_PHOTO_REQUIRED");
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("RETURN_PHOTO_TOO_LARGE");
            return bytes;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("RETURN_PHOTO_")) throw ex;
            throw new IllegalArgumentException("RETURN_PHOTO_INVALID");
        }
    }

    private String hash(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** Caller has locked the owned delivery and is changing its status in this same transaction. */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void save(long jobId, String actor, String reason, String base64) throws Exception {
        byte[] bytes = decode(base64);
        String digest = hash(bytes);
        var existing = jdbc.queryForList("SELECT reason,upload_sha256 FROM delivery_return_photos WHERE delivery_job_id=?", jobId);
        if (!existing.isEmpty()) {
            if (reason.equals(existing.get(0).get("reason")) && digest.equals(existing.get(0).get("upload_sha256"))) return;
            throw new IllegalArgumentException("RETURN_PHOTO_ALREADY_SAVED");
        }
        byte[] jpeg = normalize(bytes);
        int saved = jdbc.update("INSERT INTO delivery_return_photos(delivery_job_id,order_id,actor,reason,photo_jpeg,upload_sha256,created_at) " +
                "SELECT id,order_id,?,?,?,?,UTC_TIMESTAMP() FROM delivery_jobs WHERE id=? AND status IN ('FAILED','DELIVERED')",
                actor, reason, jpeg, digest, jobId);
        if (saved != 1) throw new IllegalStateException("Delivery changed; refresh the order");
    }

    /** A retry after a lost response may acknowledge existing evidence, but never replace it. */
    public boolean matches(long jobId, String reason, String base64) throws Exception {
        String digest = hash(decode(base64));
        return Integer.valueOf(1).equals(jdbc.queryForObject(
                "SELECT COUNT(*) FROM delivery_return_photos WHERE delivery_job_id=? AND reason=? AND upload_sha256=?",
                Integer.class, jobId, reason, digest));
    }

    public String forOrder(long orderId) {
        var photos = jdbc.query("SELECT photo_jpeg FROM delivery_return_photos WHERE order_id=? ORDER BY created_at DESC LIMIT 1",
                (row, number) -> row.getBytes(1), orderId);
        return photos.isEmpty() ? null : "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(photos.get(0));
    }

    static byte[] normalize(byte[] bytes) throws Exception {
        // Inspect dimensions before decoding to avoid allocating unbounded image buffers.
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("RETURN_PHOTO_INVALID");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!"JPEG".equalsIgnoreCase(format) && !"PNG".equalsIgnoreCase(format))
                    throw new IllegalArgumentException("RETURN_PHOTO_INVALID");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 24_000_000L)
                    throw new IllegalArgumentException("RETURN_PHOTO_TOO_LARGE");
                var parameters = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 1024);
                parameters.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage source = reader.read(0, parameters);
                double scale = Math.min(1d, 1024d / Math.max(source.getWidth(), source.getHeight()));
                BufferedImage result = new BufferedImage(Math.max(1, (int) Math.round(source.getWidth() * scale)),
                        Math.max(1, (int) Math.round(source.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
                try {
                    Graphics2D graphics = result.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(source, 0, 0, result.getWidth(), result.getHeight(), null);
                    } finally { graphics.dispose(); }
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    if (!ImageIO.write(result, "jpeg", output)) throw new IllegalStateException("RETURN_PHOTO_INVALID");
                    return output.toByteArray();
                } finally { source.flush(); result.flush(); }
            } finally { reader.dispose(); }
        }
    }
}
