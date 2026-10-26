package com.ninimum.api.taxi;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.warehouse.WarehouseException;
import com.ninimum.api.warehouse.WarehouseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;

/** Web admin: book the taxi, mark it delivered or not delivered. Path is under orders, so staff need orders.edit. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/admin/management/orders/{orderId}/taxi")
public class TaxiDeliveryController extends BaseController {
    private final TaxiDelivery taxi;
    @GetMapping public Object details(@PathVariable long orderId) { return setResult(Result.SUCCESS, taxi.details(orderId)); }
    @PostMapping("/book") public Object book(@PathVariable long orderId, @RequestBody Map<String,Object> body) { taxi.book(orderId, body, WarehouseService.actor()); return setResult(Result.SUCCESS, taxi.details(orderId)); }
    @PostMapping("/delivered") public Object delivered(@PathVariable long orderId) { taxi.delivered(orderId, WarehouseService.actor()); return setResult(Result.SUCCESS, taxi.details(orderId)); }
    @PostMapping("/failed") public Object failed(@PathVariable long orderId, @RequestBody Map<String,Object> body) { taxi.failed(orderId, Objects.toString(body.get("reason"), ""), WarehouseService.actor()); return setResult(Result.SUCCESS, taxi.details(orderId)); }
    @ExceptionHandler(WarehouseException.class) public ResponseEntity<Object> invalid(WarehouseException ex) {
        var r = setResult(Result.FAILED); r.setResultCode(ex.getMessage()); r.setResultMsg(ex.getMessage());
        return new ResponseEntity<>(r, HttpStatus.CONFLICT);
    }
}
