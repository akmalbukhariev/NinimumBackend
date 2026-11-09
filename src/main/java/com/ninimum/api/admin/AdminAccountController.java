package com.ninimum.api.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;

/**
 * TZ v13 item 2: the owner edits an administrator (name, login, new password) and can close their session;
 * every staff member changes their own password (old + new). Changes go to Kirish tarixi.
 * Paths: /admin/management/admins/** is owner-only (StaffAccess rules), /admin/me/password is open to any signed-in staff.
 */
@RestController
@RequestMapping("/ninimum/api/v1/admin")
public class AdminAccountController {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    public AdminAccountController(JdbcTemplate jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @PutMapping("/management/admins/{id}")
    @Transactional
    public Map<String,Object> edit(@PathVariable long id, @RequestBody Map<String,Object> body) {
        owner();
        var rows = jdbc.queryForList("SELECT login_id,name FROM admin_users WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) return fail("ADMIN_NOT_FOUND");
        String oldLogin = (String) rows.get(0).get("login_id");
        String name = text(body.get("name")), login = text(body.get("login_id"));
        if (name.isEmpty() || login.isEmpty() || name.length() > 100 || login.length() > 50) return fail("ADMIN_INVALID_INPUT");
        if (!login.equalsIgnoreCase(oldLogin) && jdbc.queryForObject("SELECT COUNT(*) FROM admin_users WHERE UPPER(login_id)=UPPER(?) AND id<>?", Integer.class, login, id) > 0)
            return fail("ADMIN_LOGIN_TAKEN");
        String password = body.get("password") == null || String.valueOf(body.get("password")).isEmpty() ? null : String.valueOf(body.get("password"));
        if (password != null && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) return fail("ADMIN_WEAK_PASSWORD");
        boolean loginChanged = !login.equals(oldLogin), nameChanged = !name.equals(Objects.toString(rows.get(0).get("name"), ""));
        jdbc.update("UPDATE admin_users SET name=?,login_id=? WHERE id=?", name, login, id);
        if (password != null) jdbc.update("UPDATE admin_users SET password=? WHERE id=?", encoder.encode(password), id);
        // A new login or password signs the account out everywhere.
        if (loginChanged || password != null) jdbc.update("DELETE FROM admin_app_sessions WHERE login_id IN (?,?)", oldLogin, login);
        if (loginChanged || nameChanged) audit(login, "ACCOUNT_CHANGED");
        if (password != null) audit(login, "PASSWORD_RESET");
        return Map.of("resultCode", "100", "resultData", Map.of("id", id, "login_id", login, "name", name, "signed_out", loginChanged || password != null));
    }

    @PostMapping("/management/admins/{id}/sign-out")
    @Transactional
    public Map<String,Object> signOut(@PathVariable long id) {
        owner();
        var rows = jdbc.queryForList("SELECT login_id FROM admin_users WHERE id=?", id);
        if (rows.isEmpty()) return fail("ADMIN_NOT_FOUND");
        String login = (String) rows.get(0).get("login_id");
        if (login.equals(current())) return fail("ADMIN_SELF_SIGN_OUT");
        jdbc.update("DELETE FROM admin_app_sessions WHERE login_id=?", login);
        audit(login, "SESSION_CLOSED");
        return Map.of("resultCode", "100", "resultData", Map.of("id", id));
    }

    @PutMapping("/me/password")
    @Transactional
    public Map<String,Object> ownPassword(@RequestBody Map<String,Object> body) {
        String login = current();
        var rows = jdbc.queryForList("SELECT password FROM admin_users WHERE login_id=? FOR UPDATE", login);
        if (rows.isEmpty()) return fail("ADMIN_NOT_FOUND");
        String old = body.get("old_password") == null ? "" : String.valueOf(body.get("old_password"));
        if (!encoder.matches(old, (String) rows.get(0).get("password"))) return fail("ADMIN_OLD_PASSWORD_WRONG");
        Object next0 = body.get("new_password");
        if (!(next0 instanceof String next) || next.isEmpty() || next.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) return fail("ADMIN_WEAK_PASSWORD");
        jdbc.update("UPDATE admin_users SET password=? WHERE login_id=?", encoder.encode(next), login);
        // Only one session per account exists, and it is this one: other devices were already signed out at this login.
        audit(login, "PASSWORD_CHANGED");
        return Map.of("resultCode", "100", "resultData", Map.of("changed", true));
    }

    private void audit(String login, String outcome) {
        jdbc.update("INSERT INTO login_attempt_audit(realm,identity_key,outcome,occurred_at) VALUES('ADMIN',SHA2(UPPER(TRIM(?)),256),?,UTC_TIMESTAMP())", login, outcome);
    }
    private static String current() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private static void owner() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "SUPER_ADMIN".equals(a.getAuthority())))
            throw new org.springframework.security.access.AccessDeniedException("ADMIN_PERMISSION_DENIED");
    }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private static Map<String,Object> fail(String code) { return Map.of("resultCode", code, "resultMsg", code); }
}
