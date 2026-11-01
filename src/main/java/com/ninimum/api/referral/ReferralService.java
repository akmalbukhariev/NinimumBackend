package com.ninimum.api.referral;

import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.*;

/**
 * Promo period. While promo_mode is on, tariffs can't be bought. A new customer who enters a friend's code at
 * registration gets one free month of the chosen tariff, and that friend's counter goes up; at friends_needed
 * the friend gets one free month too. With welcome_for_all on, every new customer gets the month, code or not.
 * A free month is an ACTIVE user_tariffs row, so prices and delivery follow.
 */
@Service
@RequiredArgsConstructor
public class ReferralService {
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    private Map<String,Object> settings() { return jdbc.queryForMap("SELECT * FROM referral_settings WHERE id=1"); }
    private static boolean on(Object value) { return value instanceof Boolean b ? b : value != null && !"0".equals(value.toString()); }
    /** False before the migration, so the backend can be deployed first. */
    public boolean promoMode() {
        try { return on(settings().get("promo_mode")); } catch (DataAccessException ex) { return false; }
    }
    public Map<String,Object> publicSettings() {
        try { var s = settings(); return Map.of("promo_mode", on(s.get("promo_mode")), "friends_needed", s.get("friends_needed")); }
        catch (DataAccessException ex) { return Map.of("promo_mode", false, "friends_needed", 5); }
    }
    private static String clean(String code) { return code == null ? "" : code.trim().toUpperCase(Locale.ROOT); }
    private Long inviter(String code, boolean lock) {
        var rows = jdbc.queryForList("SELECT c.user_id FROM referral_codes c JOIN users u ON u.id=c.user_id WHERE c.code=? AND u.status='ACTIVE' AND u.is_active=1" + (lock ? " FOR UPDATE" : ""), Long.class, clean(code));
        return rows.isEmpty() ? null : rows.get(0);
    }
    public boolean validCode(String code) { return inviter(code, false) != null; }

    private String code(long user) {
        for (int attempt = 0; attempt < 20; attempt++) {
            var rows = jdbc.queryForList("SELECT code FROM referral_codes WHERE user_id=?", String.class, user);
            if (!rows.isEmpty()) return rows.get(0);
            var next = new StringBuilder("N");
            for (int i = 0; i < 6; i++) next.append(LETTERS.charAt(random.nextInt(LETTERS.length())));
            jdbc.update("INSERT IGNORE INTO referral_codes(user_id,code,created_at) VALUES(?,?,UTC_TIMESTAMP())", user, next.toString());
        }
        throw new IllegalStateException("Referral code could not be created");
    }
    private int friends(long user) { return jdbc.queryForObject("SELECT COUNT(*) FROM referrals WHERE inviter_user_id=?", Integer.class, user); }
    private boolean rewarded(long user) { return jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards WHERE user_id=? AND kind='INVITER'", Integer.class, user) > 0; }

    /** One free month, once per kind. A customer who already has a tariff keeps it and gets a month longer. */
    private void grant(long user, String kind, long plan) {
        if (jdbc.update("INSERT IGNORE INTO referral_rewards(user_id,kind,created_at) VALUES(?,?,UTC_TIMESTAMP())", user, kind) == 0) return;
        jdbc.update("UPDATE user_tariffs SET status='EXPIRED',updated_at=NOW() WHERE user_id=? AND status='ACTIVE' AND end_date IS NOT NULL AND end_date<NOW()", user);
        var active = jdbc.queryForList("SELECT id FROM user_tariffs WHERE user_id=? AND status='ACTIVE' AND end_date IS NOT NULL ORDER BY end_date DESC LIMIT 1", Long.class, user);
        long tariff;
        if (active.isEmpty()) {
            jdbc.update("INSERT INTO user_tariffs(user_id,tariff_plan_id,status,start_date,end_date,auto_renew,activated_at,created_at) VALUES(?,?,'ACTIVE',NOW(),DATE_ADD(NOW(),INTERVAL 1 MONTH),0,NOW(),NOW())", user, plan);
            tariff = jdbc.queryForObject("SELECT MAX(id) FROM user_tariffs WHERE user_id=?", Long.class, user);
        } else {
            tariff = active.get(0);
            jdbc.update("UPDATE user_tariffs SET end_date=DATE_ADD(end_date,INTERVAL 1 MONTH),updated_at=NOW() WHERE id=?", tariff);
        }
        jdbc.update("UPDATE referral_rewards SET user_tariff_id=? WHERE user_id=? AND kind=?", tariff, user, kind);
    }
    private void rewardInviter(long user, Map<String,Object> s) {
        if (s.get("tariff_plan_id") != null && friends(user) >= ((Number) s.get("friends_needed")).intValue())
            grant(user, "INVITER", ((Number) s.get("tariff_plan_id")).longValue());
    }

    /** Called inside registration: the new customer's month and the inviter's counter. The code was checked before. */
    @Transactional(rollbackFor = Exception.class)
    public void registered(String phone, String rawCode) {
        if (!promoMode()) return;
        var s = settings();
        boolean hasCode = !clean(rawCode).isEmpty();
        if (!hasCode && !on(s.get("welcome_for_all"))) return;
        Long inviter = hasCode ? inviter(rawCode, true) : null;
        if (hasCode && inviter == null) throw new IllegalArgumentException("Referral code not found");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE phone_number=?", Long.class, phone);
        if (hasCode) jdbc.update("INSERT INTO referrals(invited_user_id,inviter_user_id,code,created_at) VALUES(?,?,?,UTC_TIMESTAMP())", user, inviter, clean(rawCode));
        grant(user, "WELCOME", ((Number) s.get("tariff_plan_id")).longValue());
        if (hasCode) rewardInviter(inviter, s);
    }

    /**
     * Keeps an account in step with the welcome switch, so a change applies at once to registered accounts too.
     * Called whenever the app reads the active tariff. On: an account with no tariff that never had the month gets it.
     * Off: a month given without a code is taken back. Paid tariffs and months from a code or from inviting stay.
     */
    @Transactional(rollbackFor = Exception.class)
    public void sync(long user) {
        if (!promoMode()) return;
        var s = settings();
        var given = jdbc.queryForList("SELECT user_tariff_id FROM referral_rewards WHERE user_id=? AND kind='WELCOME'", Long.class, user);
        if (on(s.get("welcome_for_all"))) {
            if (given.isEmpty() && s.get("tariff_plan_id") != null && jdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_tariffs WHERE user_id=? AND status='ACTIVE' AND (end_date IS NULL OR end_date>=NOW())", Integer.class, user) == 0)
                grant(user, "WELCOME", ((Number) s.get("tariff_plan_id")).longValue());
            return;
        }
        if (given.isEmpty() || jdbc.queryForObject("SELECT COUNT(*) FROM referrals WHERE invited_user_id=?", Integer.class, user) > 0) return;
        Long tariff = given.get(0);
        if (tariff != null && jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE user_tariff_id=? AND status='PAID'", Integer.class, tariff) == 0
                && jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards WHERE user_tariff_id=? AND kind='INVITER'", Integer.class, tariff) == 0)
            jdbc.update("UPDATE user_tariffs SET status='EXPIRED',end_date=NOW(),updated_at=NOW() WHERE id=? AND status='ACTIVE'", tariff);
        jdbc.update("DELETE FROM referral_rewards WHERE user_id=? AND kind='WELCOME'", user);
    }

    /** The customer's Invite page. A lowered friends_needed takes effect here for customers already past it. */
    @Transactional(rollbackFor = Exception.class)
    public Map<String,Object> me(long user) {
        var s = settings();
        if (on(s.get("promo_mode"))) rewardInviter(user, s);
        var out = new LinkedHashMap<String,Object>();
        out.put("promo_mode", on(s.get("promo_mode")));
        out.put("code", code(user));
        out.put("friends_count", friends(user));
        out.put("friends_needed", s.get("friends_needed"));
        out.put("rewarded", rewarded(user));
        return out;
    }

    public Map<String,Object> admin() {
        var s = settings();
        var out = new LinkedHashMap<String,Object>();
        out.put("promo_mode", on(s.get("promo_mode")));
        out.put("welcome_for_all", on(s.get("welcome_for_all")));
        out.put("friends_needed", s.get("friends_needed"));
        out.put("tariff_plan_id", s.get("tariff_plan_id"));
        out.put("plans", jdbc.queryForList("SELECT id,name FROM tariff_plans WHERE is_active=1 ORDER BY name"));
        out.put("friends_total", jdbc.queryForObject("SELECT COUNT(*) FROM referrals", Integer.class));
        out.put("free_months", jdbc.queryForObject("SELECT COUNT(*) FROM referral_rewards", Integer.class));
        out.put("rows", jdbc.queryForList("SELECT r.invited_user_id,TRIM(CONCAT(n.first_name,' ',COALESCE(n.last_name,''))) invited_name,n.phone_number invited_phone," +
                "r.inviter_user_id,TRIM(CONCAT(i.first_name,' ',COALESCE(i.last_name,''))) inviter_name,i.phone_number inviter_phone,r.code," +
                "DATE_FORMAT(r.created_at,'%Y-%m-%dT%H:%i:%sZ') created_at,(SELECT COUNT(*) FROM referrals x WHERE x.inviter_user_id=r.inviter_user_id) inviter_friends," +
                "EXISTS(SELECT 1 FROM referral_rewards w WHERE w.user_id=r.inviter_user_id AND w.kind='INVITER') inviter_rewarded " +
                "FROM referrals r LEFT JOIN users n ON n.id=r.invited_user_id LEFT JOIN users i ON i.id=r.inviter_user_id ORDER BY r.created_at DESC,r.invited_user_id DESC LIMIT 500"));
        return out;
    }
    @Transactional(rollbackFor = Exception.class)
    public void save(Map<String,Object> body) {
        boolean promo = on(body.get("promo_mode"));
        int needed; Long plan = null;
        try {
            needed = Integer.parseInt(String.valueOf(body.get("friends_needed")).trim());
            var value = body.get("tariff_plan_id");
            if (value != null && !value.toString().isBlank()) plan = Long.valueOf(value.toString().trim());
        } catch (NumberFormatException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
        if (needed < 1 || needed > 100) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        if (plan != null && jdbc.queryForObject("SELECT COUNT(*) FROM tariff_plans WHERE id=? AND is_active=1", Integer.class, plan) == 0) throw new WarehouseException("WAREHOUSE_INVALID_INPUT");
        if (promo && plan == null) throw new WarehouseException("REFERRAL_PLAN_REQUIRED");
        jdbc.update("UPDATE referral_settings SET promo_mode=?,welcome_for_all=?,friends_needed=?,tariff_plan_id=?,updated_by=?,updated_at=UTC_TIMESTAMP() WHERE id=1", promo, on(body.get("welcome_for_all")), needed, plan, WarehouseService.actor());
    }
}
