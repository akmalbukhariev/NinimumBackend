package com.ninimum.api.admin.access;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.warehouse.WarehouseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** M14: owner settings for the strictness rules (Settings → Qat’iylik qoidalari); the warehouse app reads Q-14. */
@RestController
@RequiredArgsConstructor
public class StrictRulesController extends BaseController {
    private final StrictRules rules;
    @GetMapping("/ninimum/api/v1/admin/management/rules") @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Object list() { return setResult(Result.SUCCESS, rules.list()); }
    @PutMapping("/ninimum/api/v1/admin/management/rules/{code}") @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Object update(@PathVariable String code, @RequestBody Map<String,Object> body) { rules.update(code, body); return setResult(Result.SUCCESS, rules.list()); }
    /** Q-14: when on, the warehouse app offers no manual code entry for products that have a barcode. */
    @GetMapping("/ninimum/api/v1/warehouse-app/rules")
    public Object warehouseApp() { return setResult(Result.SUCCESS, Map.of("scanner_required", StrictRules.on("Q-14"))); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
