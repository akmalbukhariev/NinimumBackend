package com.ninimum.api.referral;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.security.CurrentCustomer;
import com.ninimum.api.warehouse.WarehouseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReferralController extends BaseController {
    private final ReferralService referral;
    private final CurrentCustomer customer;
    /** Open: the registration screen needs it before sign-in. */
    @GetMapping("/ninimum/api/v1/referral/settings")
    public Object settings() { return setResult(Result.SUCCESS, referral.publicSettings()); }
    @GetMapping("/ninimum/api/v1/referral/me")
    public Object me() throws Exception { return setResult(Result.SUCCESS, referral.me(customer.id())); }
    @GetMapping("/ninimum/api/v1/admin/management/referral") @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Object admin() { return setResult(Result.SUCCESS, referral.admin()); }
    @PutMapping("/ninimum/api/v1/admin/management/referral") @PreAuthorize("hasAuthority('SUPER_ADMIN')")
    public Object save(@RequestBody Map<String,Object> body) { referral.save(body); return setResult(Result.SUCCESS, referral.admin()); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
