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

/** M14: owner-only staff roles and permissions (Settings → Administratorlar). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management/access")
@PreAuthorize("hasAuthority('SUPER_ADMIN')")
public class StaffAccessController extends BaseController {
    private final StaffAccessService service;
    @GetMapping public Object overview() { return setResult(Result.SUCCESS, service.overview()); }
    @PutMapping("/{id}") public Object update(@PathVariable long id, @RequestBody Map<String,Object> body) { return setResult(Result.SUCCESS, service.update(id, body)); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
