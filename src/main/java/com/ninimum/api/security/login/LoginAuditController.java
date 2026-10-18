package com.ninimum.api.security.login;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/ninimum/api/v1/admin/management/security")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class LoginAuditController {
    private final JdbcTemplate jdbc;
    public LoginAuditController(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @GetMapping("/login-audit") public Object audit(@RequestParam(defaultValue="1") int page) {
        int p=Math.max(1,Math.min(page,1000000));
        return Map.of("resultCode","100","resultData",Map.of("items",jdbc.queryForList("SELECT h.id,h.realm,h.identity_key,COALESCE(a.login_id,w.worker_code,d.worker_code) account_label,h.outcome,DATE_FORMAT(h.occurred_at,'%Y-%m-%dT%H:%i:%sZ') occurred_at FROM login_attempt_audit h LEFT JOIN admin_users a ON h.realm='ADMIN' AND SHA2(UPPER(TRIM(a.login_id)),256)=h.identity_key LEFT JOIN warehouse_workers w ON h.realm='WAREHOUSE' AND SHA2(UPPER(TRIM(w.worker_code)),256)=h.identity_key LEFT JOIN delivery_app_workers d ON h.realm='COURIER' AND SHA2(UPPER(TRIM(d.worker_code)),256)=h.identity_key ORDER BY h.id DESC LIMIT 50 OFFSET ?",(p-1)*50),"total",jdbc.queryForObject("SELECT COUNT(*) FROM login_attempt_audit",Long.class),"page",p,"page_size",50));
    }
}
