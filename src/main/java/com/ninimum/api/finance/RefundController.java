package com.ninimum.api.finance;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.warehouse.WarehouseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** K5 "Qaytarish kerak". The list needs problems.view; recording a refund needs money.edit (StaffAccess rules). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management/refunds")
public class RefundController extends BaseController {
    private final RefundService service;
    @GetMapping public Object list() { return setResult(Result.SUCCESS, service.list()); }
    @PostMapping("/{orderId}/record") public Object record(@PathVariable long orderId, @RequestBody Map<String,Object> body) { service.recordPartial(orderId, body); return setResult(Result.SUCCESS, service.list()); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
