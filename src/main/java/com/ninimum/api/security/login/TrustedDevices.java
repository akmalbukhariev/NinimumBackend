package com.ninimum.api.security.login;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** A phone that confirmed an SMS code: its key lets the app sign in again without another SMS. Only a hash is stored. */
@Service
public class TrustedDevices {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public TrustedDevices(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    /** New key for this account, or null before the migration (then every sign-in asks for an SMS code). */
    public String create(long user) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        try {
            jdbc.update("INSERT INTO trusted_devices(token_hash,user_id,created_at,last_used_at) VALUES(?,?,UTC_TIMESTAMP(),UTC_TIMESTAMP())", hash(token), user);
            return token;
        } catch (DataAccessException ex) { return null; }
    }

    public boolean valid(long user, String token) {
        if (token == null || token.isBlank() || token.length() > 100) return false;
        try {
            return jdbc.update("UPDATE trusted_devices SET last_used_at=UTC_TIMESTAMP() WHERE token_hash=? AND user_id=? AND last_used_at>=UTC_TIMESTAMP()-INTERVAL 180 DAY", hash(token), user) == 1;
        } catch (DataAccessException ex) { return false; }
    }
}
