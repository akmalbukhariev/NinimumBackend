package com.ninimum.api.admin.access;

import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** M14: the owner sets each staff member's role and single permission additions/removals; every change is logged. */
@Service
@RequiredArgsConstructor
public class StaffAccessService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Map<String,Object> overview() {
        owner();
        var admins = new ArrayList<Map<String,Object>>();
        for (var row : jdbc.queryForList("SELECT id,login_id,name,role,status,staff_role,permissions_added,permissions_removed FROM admin_users ORDER BY id")) {
            var item = new LinkedHashMap<String,Object>();
            item.put("id", row.get("id")); item.put("login_id", row.get("login_id")); item.put("name", row.get("name"));
            item.put("role", row.get("role")); item.put("status", row.get("status")); item.put("staff_role", row.get("staff_role"));
            item.put("added", StaffAccess.split((String) row.get("permissions_added")));
            item.put("removed", StaffAccess.split((String) row.get("permissions_removed")));
            item.put("permissions", "SUPER_ADMIN".equals(row.get("role")) ? StaffAccess.ALL
                    : StaffAccess.effective((String) row.get("staff_role"), (String) row.get("permissions_added"), (String) row.get("permissions_removed")));
            admins.add(item);
        }
        return Map.of("roles", StaffAccess.ROLES, "permissions", StaffAccess.ALL, "admins", admins);
    }

    /** Body: staff_role, permissions (the full wanted list); stored as differences from the role defaults. */
    @Transactional
    public Map<String,Object> update(long id, Map<String,Object> body) {
        owner();
        String role = Objects.toString(body.get("staff_role"), "");
        if (!StaffAccess.ROLES.containsKey(role)) throw new WarehouseException("ADMIN_INVALID_ROLE");
        var wanted = new TreeSet<String>();
        if (body.get("permissions") instanceof Collection<?> list) for (Object p : list) wanted.add(String.valueOf(p));
        if (!StaffAccess.ALL.containsAll(wanted)) throw new WarehouseException("ADMIN_INVALID_PERMISSION");
        var rows = jdbc.queryForList("SELECT role,staff_role,permissions_added,permissions_removed FROM admin_users WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new WarehouseException("WAREHOUSE_NOT_FOUND");
        var row = rows.get(0);
        if (!"ADMIN".equals(row.get("role"))) throw new WarehouseException("ADMIN_OWNER_ACCESS_FIXED");
        // "x.edit" always brings "x.view"; a wanted view-only area drops its edit.
        for (String p : List.copyOf(wanted)) if (p.endsWith(".edit")) wanted.add(p.substring(0, p.length() - 5) + ".view");
        var defaults = StaffAccess.effective(role, null, null);
        var added = new TreeSet<>(wanted); added.removeAll(defaults);
        var removed = new TreeSet<>(defaults); removed.removeAll(wanted);
        String before = row.get("staff_role") + " +" + Objects.toString(row.get("permissions_added"), "") + " -" + Objects.toString(row.get("permissions_removed"), "");
        String addedText = String.join(",", added), removedText = String.join(",", removed);
        jdbc.update("UPDATE admin_users SET staff_role=?,permissions_added=?,permissions_removed=? WHERE id=?", role, addedText, removedText, id);
        jdbc.update("INSERT INTO admin_access_audit(admin_id,changed_by,old_value,new_value,changed_at) VALUES(?,?,?,?,UTC_TIMESTAMP())",
                id, WarehouseService.actor(), before, role + " +" + addedText + " -" + removedText);
        return Map.of("id", id, "staff_role", role, "permissions", StaffAccess.effective(role, addedText, removedText));
    }

    private static void owner() { if (!StaffAccess.isOwner()) throw new AccessDeniedException("Owner only"); }
}
