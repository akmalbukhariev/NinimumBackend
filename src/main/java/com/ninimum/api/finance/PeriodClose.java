package com.ninimum.api.finance;

import com.ninimum.api.admin.access.StaffAccess;
import com.ninimum.api.admin.access.StrictRules;
import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

/**
 * Q-16 (TZ v10 §10, test T20): the owner closes the books up to a date. While Q-16 is on for the user,
 * money documents, supplier payments, receipts and inventory documents dated on or before it can't be
 * created, approved or reversed. Reopening (an earlier date or none) is owner-only and needs a reason.
 * Without the table (tests, before the migration) nothing is closed.
 */
@Component
public class PeriodClose {
    private static volatile JdbcTemplate shared;
    private final JdbcTemplate jdbc;
    public PeriodClose(JdbcTemplate jdbc) { this.jdbc = jdbc; shared = jdbc; }

    public static LocalDate closedUntil() {
        JdbcTemplate db = shared;
        if (db != null) try {
            var rows = db.queryForList("SELECT closed_until FROM period_closings ORDER BY id DESC LIMIT 1");
            if (!rows.isEmpty() && rows.get(0).get("closed_until") != null) return LocalDate.parse(rows.get(0).get("closed_until").toString().substring(0, 10));
        } catch (DataAccessException ex) { /* not migrated yet */ }
        return null;
    }
    /** Rejects a document dated in the closed period. */
    public static void guard(LocalDate day) {
        if (day == null) return;
        LocalDate closed = closedUntil();
        if (closed != null && !day.isAfter(closed) && StrictRules.on("Q-16")) throw new WarehouseException("PERIOD_CLOSED");
    }
    /** For documents that take today's date (receipts, inventory). */
    public static void guardToday() { guard(FinanceService.today()); }

    public Map<String,Object> state() {
        owner();
        var out = new LinkedHashMap<String,Object>();
        LocalDate closed = closedUntil();
        out.put("closed_until", closed == null ? null : closed.toString());
        List<Map<String,Object>> history;
        try { history = jdbc.queryForList("SELECT id,DATE_FORMAT(closed_until,'%Y-%m-%d') closed_until,reason,changed_by,DATE_FORMAT(changed_at,'%Y-%m-%dT%H:%i:%sZ') changed_at FROM period_closings ORDER BY id DESC LIMIT 20"); }
        catch (DataAccessException ex) { history = List.of(); }
        out.put("history", history);
        return out;
    }

    @Transactional
    public Map<String,Object> set(Map<String,Object> body) {
        owner();
        String raw = Objects.toString(body.get("closed_until"), "").trim();
        LocalDate until;
        try { until = raw.isEmpty() ? null : LocalDate.parse(raw); } catch (RuntimeException ex) { throw new WarehouseException("WAREHOUSE_INVALID_INPUT"); }
        if (until != null && until.isAfter(FinanceService.today())) throw new WarehouseException("PERIOD_FUTURE_DATE");
        jdbc.queryForList("SELECT id FROM period_closings ORDER BY id DESC LIMIT 1 FOR UPDATE");
        LocalDate before = closedUntil();
        if (Objects.equals(before, until)) return state();
        boolean reopen = before != null && (until == null || until.isBefore(before));
        String reason = FinanceService.text(body.get("reason"), 500, false);
        if (reason != null && reason.isEmpty()) reason = null;
        if (reopen && reason == null) throw new WarehouseException("PERIOD_REASON_REQUIRED");
        jdbc.update("INSERT INTO period_closings(closed_until,reason,changed_by,changed_at) VALUES(?,?,?,UTC_TIMESTAMP())", until, reason, WarehouseService.actor());
        return state();
    }

    private static void owner() { if (!StaffAccess.isOwner()) throw new AccessDeniedException("Owner only"); }
}
