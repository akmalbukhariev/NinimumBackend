package com.ninimum.api.admin.access;

import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/**
 * M14 (TZ v10 §10): strictness rules Q-1…Q-18. Each switchable rule is on/off separately for staff and for the owner.
 * Services call {@link #on(String)}; without the table (tests, before the migration) the defaults below apply.
 */
@Component
public class StrictRules {
    /** SWITCH: the owner turns it on/off here. ALWAYS: built in. LINKED: has its own settings page. MISSING: module not built yet. */
    public record Rule(String code, String kind, boolean staff, boolean owner, String value) {}
    // Defaults follow TZ v10, except where the system already enforced a rule for the owner too (Q-5, Q-8, Q-9),
    // and Q-14, which stays off until barcodes and scanners are in use (M7/M5 are deferred).
    public static final List<Rule> RULES = List.of(
        new Rule("Q-1","ALWAYS",true,true,null), new Rule("Q-2","ALWAYS",true,true,null),
        new Rule("Q-3","MISSING",true,false,null), new Rule("Q-4","MISSING",true,false,"20%"),
        new Rule("Q-5","SWITCH",true,true,null), new Rule("Q-6","SWITCH",true,false,null),
        new Rule("Q-7","LINKED",true,true,null), new Rule("Q-8","SWITCH",true,true,null),
        new Rule("Q-9","SWITCH",true,true,null), new Rule("Q-10","SWITCH",true,false,null),
        new Rule("Q-11","LINKED",true,true,null), new Rule("Q-12","ALWAYS",true,true,null),
        new Rule("Q-13","ALWAYS",true,true,null), new Rule("Q-14","SWITCH",false,false,null),
        new Rule("Q-15","LINKED",true,true,null), new Rule("Q-16","MISSING",true,true,null),
        new Rule("Q-17","ALWAYS",true,true,null), new Rule("Q-18","MISSING",true,false,null));
    private static final Map<String,Rule> BY_CODE = new HashMap<>();
    static { for (Rule r : RULES) BY_CODE.put(r.code(), r); }

    private static volatile JdbcTemplate shared;
    private final JdbcTemplate jdbc;
    public StrictRules(JdbcTemplate jdbc) { this.jdbc = jdbc; shared = jdbc; }

    /** Whether the rule applies to the current user (owner or staff; couriers and warehouse workers count as staff). */
    public static boolean on(String code) {
        Rule rule = BY_CODE.get(code);
        if (rule == null) throw new IllegalArgumentException(code);
        boolean owner = StaffAccess.isOwner();
        if (!"SWITCH".equals(rule.kind())) return owner ? rule.owner() : rule.staff();
        JdbcTemplate db = shared;
        if (db != null) try {
            var rows = db.queryForList("SELECT staff_enabled,owner_enabled FROM strict_rules WHERE code=?", code);
            if (!rows.isEmpty()) return truth(rows.get(0).get(owner ? "owner_enabled" : "staff_enabled"));
        } catch (DataAccessException ex) { /* table not migrated yet: defaults */ }
        return owner ? rule.owner() : rule.staff();
    }
    private static boolean truth(Object v) { return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() != 0; }

    public List<Map<String,Object>> list() {
        owner();
        var stored = new HashMap<String,Map<String,Object>>();
        try { for (var row : jdbc.queryForList("SELECT code,staff_enabled,owner_enabled,updated_by,DATE_FORMAT(updated_at,'%Y-%m-%dT%H:%i:%sZ') updated_at FROM strict_rules")) stored.put(String.valueOf(row.get("code")), row); }
        catch (DataAccessException ex) { /* defaults only */ }
        var out = new ArrayList<Map<String,Object>>();
        for (Rule r : RULES) {
            var row = stored.get(r.code());
            var item = new LinkedHashMap<String,Object>();
            item.put("code", r.code()); item.put("kind", r.kind()); item.put("value", r.value());
            boolean sw = "SWITCH".equals(r.kind()) && row != null;
            item.put("staff_enabled", sw ? truth(row.get("staff_enabled")) : r.staff());
            item.put("owner_enabled", sw ? truth(row.get("owner_enabled")) : r.owner());
            item.put("updated_by", row == null ? null : row.get("updated_by")); item.put("updated_at", row == null ? null : row.get("updated_at"));
            out.add(item);
        }
        return out;
    }

    @Transactional
    public void update(String code, Map<String,Object> body) {
        owner();
        Rule rule = BY_CODE.get(code);
        if (rule == null || !"SWITCH".equals(rule.kind())) throw new WarehouseException("RULE_NOT_SWITCHABLE");
        boolean staff = Boolean.TRUE.equals(body.get("staff_enabled")), owner = Boolean.TRUE.equals(body.get("owner_enabled"));
        String actor = WarehouseService.actor();
        var old = jdbc.queryForList("SELECT staff_enabled,owner_enabled FROM strict_rules WHERE code=? FOR UPDATE", code);
        String before = old.isEmpty() ? "default" : "staff=" + truth(old.get(0).get("staff_enabled")) + " owner=" + truth(old.get(0).get("owner_enabled"));
        jdbc.update("INSERT INTO strict_rules(code,staff_enabled,owner_enabled,updated_by,updated_at) VALUES(?,?,?,?,UTC_TIMESTAMP()) " +
                "ON DUPLICATE KEY UPDATE staff_enabled=VALUES(staff_enabled),owner_enabled=VALUES(owner_enabled),updated_by=VALUES(updated_by),updated_at=VALUES(updated_at)",
                code, staff, owner, actor);
        jdbc.update("INSERT INTO strict_rules_audit(code,changed_by,old_value,new_value,changed_at) VALUES(?,?,?,?,UTC_TIMESTAMP())",
                code, actor, before, "staff=" + staff + " owner=" + owner);
    }

    private static void owner() { if (!StaffAccess.isOwner()) throw new AccessDeniedException("Owner only"); }
}
