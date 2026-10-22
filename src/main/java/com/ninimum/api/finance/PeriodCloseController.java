package com.ninimum.api.finance;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.warehouse.WarehouseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Q-16: owner-only period closing (Settings → Davr yopish). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management/period-close")
public class PeriodCloseController extends BaseController {
    private final PeriodClose period;
    @GetMapping @PreAuthorize("hasAuthority('SUPER_ADMIN')") public Object state() { return setResult(Result.SUCCESS, period.state()); }
    @PutMapping @PreAuthorize("hasAuthority('SUPER_ADMIN')") public Object set(@RequestBody Map<String,Object> body) { return setResult(Result.SUCCESS, period.set(body)); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
