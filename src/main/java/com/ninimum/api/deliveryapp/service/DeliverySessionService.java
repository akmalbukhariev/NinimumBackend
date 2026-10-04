package com.ninimum.api.deliveryapp.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeliverySessionService {
    private final JdbcTemplate jdbc;

    public void activate(String workerId, String token) throws Exception {
        jdbc.update("INSERT INTO delivery_app_sessions(worker_code, token_hash) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE token_hash = VALUES(token_hash)", workerId, hash(token));
    }

    public boolean isCurrent(String workerId, String token) throws Exception {
        List<String> hashes = jdbc.query("SELECT token_hash FROM delivery_app_sessions WHERE worker_code = ?",
                (rs, row) -> rs.getString(1), workerId);
        return hashes.size() == 1 && hashes.get(0).equals(hash(token));
    }

    private String hash(String token) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
