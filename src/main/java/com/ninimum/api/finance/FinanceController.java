package com.ninimum.api.finance;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.warehouse.WarehouseException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** TZ v10 §9 "Pul". Access: GET needs money.view, changes need money.edit (StaffAccess rule for /admin/finance/**). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/finance")
public class FinanceController extends BaseController {
    private final FinanceService service;
    @GetMapping("/accounts") public Object accounts() { return setResult(Result.SUCCESS, service.accounts()); }
    @PutMapping("/accounts/{id}") public Object opening(@PathVariable long id, @RequestBody Map<String,Object> body) { service.setOpening(id, body); return setResult(Result.SUCCESS, service.accounts()); }
    @GetMapping("/expense-types") public Object types() { return setResult(Result.SUCCESS, service.expenseTypes()); }
    @PostMapping("/expense-types") public Object addType(@RequestBody Map<String,Object> body) { service.saveExpenseType(null, body); return setResult(Result.SUCCESS, service.expenseTypes()); }
    @PutMapping("/expense-types/{id}") public Object saveType(@PathVariable long id, @RequestBody Map<String,Object> body) { service.saveExpenseType(id, body); return setResult(Result.SUCCESS, service.expenseTypes()); }
    @GetMapping("/documents") public Object documents(@RequestParam(required = false) String kind, @RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "1") int page) { return setResult(Result.SUCCESS, service.documents(kind, from, to, page)); }
    @PostMapping("/documents") public Object create(@RequestBody Map<String,Object> body) { return setResult(Result.SUCCESS, service.create(body)); }
    @PostMapping("/documents/{id}/reverse") public Object reverse(@PathVariable long id, @RequestBody Map<String,Object> body) { service.reverse(id, java.util.Objects.toString(body.get("reason"), "")); return setResult(Result.SUCCESS); }
    @GetMapping("/documents/{id}/photo") public Object photo(@PathVariable long id) { return setResult(Result.SUCCESS, service.photo(id)); }
    @GetMapping("/counts") public Object counts() { return setResult(Result.SUCCESS, service.counts()); }
    @PostMapping("/counts") public Object count(@RequestBody Map<String,Object> body) { return setResult(Result.SUCCESS, service.count(body)); }
    @GetMapping("/movement") public Object movement(@RequestParam(required = false) String from, @RequestParam(required = false) String to) { return setResult(Result.SUCCESS, service.movement(from, to)); }
    @GetMapping("/monthly") public Object monthly(@RequestParam(required = false) String month) { return setResult(Result.SUCCESS, service.monthly(month)); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
