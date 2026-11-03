package com.ninimum.api.push;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.security.CurrentCustomer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Each app tells the server where to send its push: after sign-in, and again when Firebase gives the phone a new address. */
@RestController
@RequiredArgsConstructor
public class PushController extends BaseController {
    private final JdbcTemplate jdbc;
    private final CurrentCustomer customer;

    private Object save(String app, String owner, Map<String, Object> body) {
        String token = Objects.toString(body.get("token"), "").trim();
        if (token.isEmpty() || token.length() > 400) return setResult(Result.FAILED);
        jdbc.update("INSERT INTO push_tokens(token,app,owner,updated_at) VALUES(?,?,?,UTC_TIMESTAMP()) ON DUPLICATE KEY UPDATE app=VALUES(app),owner=VALUES(owner),updated_at=VALUES(updated_at)", token, app, owner);
        return setResult(Result.SUCCESS);
    }
    private Object forget(Map<String, Object> body) {
        jdbc.update("DELETE FROM push_tokens WHERE token=?", Objects.toString(body.get("token"), "").trim());
        return setResult(Result.SUCCESS);
    }

    @PostMapping("/ninimum/api/v1/push/token") public Object customer(@RequestBody Map<String, Object> body) throws Exception { return save(Push.CUSTOMER, String.valueOf(customer.id()), body); }
    @PostMapping("/ninimum/api/v1/push/token/remove") public Object customerOut(@RequestBody Map<String, Object> body) { return forget(body); }
    @PostMapping("/ninimum/api/v1/warehouse-app/push/token") public Object warehouse(Authentication auth, @RequestBody Map<String, Object> body) { return save(Push.WAREHOUSE, auth.getName(), body); }
    @PostMapping("/ninimum/api/v1/warehouse-app/push/token/remove") public Object warehouseOut(@RequestBody Map<String, Object> body) { return forget(body); }
    @PostMapping("/ninimum/api/v1/delivery-app/push/token") public Object courier(Authentication auth, @RequestBody Map<String, Object> body) { return save(Push.COURIER, auth.getName(), body); }
    @PostMapping("/ninimum/api/v1/delivery-app/push/token/remove") public Object courierOut(@RequestBody Map<String, Object> body) { return forget(body); }
}
