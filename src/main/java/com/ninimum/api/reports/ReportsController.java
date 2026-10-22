package com.ninimum.api.reports;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.warehouse.WarehouseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** TZ v10 §11 reports. Signed-in staff only; each report checks its own permission (ReportsService.ACCESS). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/reports")
public class ReportsController extends BaseController {
    private final ReportsService service;
    @GetMapping public Object available() { return setResult(Result.SUCCESS, service.available()); }
    @GetMapping("/{code}") public Object run(@PathVariable String code, @RequestParam Map<String,String> filters) { return setResult(Result.SUCCESS, service.run(code, filters)); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
