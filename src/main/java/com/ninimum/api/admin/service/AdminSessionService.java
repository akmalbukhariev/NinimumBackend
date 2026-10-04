package com.ninimum.api.admin.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSessionService {
    private final JdbcTemplate jdbc;

    public void activate(String loginId, String token) throws Exception {
        jdbc.update("INSERT INTO admin_app_sessions(login_id, token_hash) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE token_hash = VALUES(token_hash)", loginId, hash(token));
    }

    public boolean isCurrent(String loginId, String token) throws Exception {
        List<String> hashes = jdbc.query("SELECT token_hash FROM admin_app_sessions WHERE login_id = ?",
                (rs, row) -> rs.getString(1), loginId);
        return hashes.size() == 1 && hashes.get(0).equals(hash(token));
    }

    private String hash(String token) throws Exception {
        byte[] bytes = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }
}
