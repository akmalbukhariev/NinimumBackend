package com.ninimum.api.taxi;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.security.CurrentCustomer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;

/** Customer app, taxi order on the way: "Mahsulotni oldim" or "Muammo bor". Always the signed-in customer's own order. */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1/order/taxi")
public class CustomerTaxiController extends BaseController {
    private final TaxiDelivery taxi;
    private final CurrentCustomer customer;

    @PostMapping("/received") public ResponseEntity<Object> received(@RequestBody Map<String,Object> body) {
        return run(() -> taxi.customerReceived(orderId(body), customer.id()));
    }
    @PostMapping("/problem") public ResponseEntity<Object> problem(@RequestBody Map<String,Object> body) {
        return run(() -> taxi.customerProblem(orderId(body), customer.id(), Objects.toString(body.get("reason"), "")));
    }

    private interface Action { void run() throws Exception; }
    private ResponseEntity<Object> run(Action action) {
        VersionResponseResult result;
        try { action.run(); result = setResult(Result.SUCCESS); }
        catch (Exception ex) { log.warn("CustomerTaxiController: {}", ex.getMessage()); result = setResult(Result.FAILED); result.setResultMsg(ex.getMessage()); }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    private static long orderId(Map<String,Object> body) throws Exception {
        try { return Long.parseLong(Objects.toString(body.get("orderId"), "")); } catch (NumberFormatException ex) { throw new Exception("Order not found"); }
    }
}
