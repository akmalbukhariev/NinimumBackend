package com.ninimum.api.delivery;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.deliveryapp.service.DeliveryProof;
import com.ninimum.api.security.CurrentCustomer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Objects;

/**
 * Courier GPS V1a.
 * Customer app (courier order delivered): "Mahsulotni oldim" or "Muammo bor" — always the signed-in customer's own order.
 * Web admin: delivery proof of an order (orders.view) and "Tekshirildi" (orders.edit).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1")
public class DeliveryProofController extends BaseController {
    private final DeliveryProof proof;
    private final CurrentCustomer customer;

    @PostMapping("/order/delivery/received") public ResponseEntity<Object> received(@RequestBody Map<String,Object> body) {
        return run(() -> { proof.customerReceived(orderId(body), customer.id()); return null; });
    }
    @PostMapping("/order/delivery/problem") public ResponseEntity<Object> problem(@RequestBody Map<String,Object> body) {
        return run(() -> { proof.customerProblem(orderId(body), customer.id(), Objects.toString(body.get("reason"), "")); return null; });
    }

    @GetMapping("/admin/management/orders/{id}/delivery-proof") public ResponseEntity<Object> forOrder(@PathVariable long id) {
        return run(() -> proof.forOrder(id));
    }
    @PostMapping("/admin/management/orders/{id}/delivery-proof/check") public ResponseEntity<Object> check(@PathVariable long id, @RequestBody Map<String,Object> body) {
        return run(() -> { proof.adminChecked(id, SecurityContextHolder.getContext().getAuthentication().getName(), Objects.toString(body.get("note"), "")); return null; });
    }

    private interface Action { Object run() throws Exception; }
    private ResponseEntity<Object> run(Action action) {
        VersionResponseResult result;
        try { Object data = action.run(); result = data == null ? setResult(Result.SUCCESS) : setResult(Result.SUCCESS, data); }
        catch (Exception ex) { log.warn("DeliveryProofController: {}", ex.getMessage()); result = setResult(Result.FAILED); result.setResultMsg(ex.getMessage()); }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
    private static long orderId(Map<String,Object> body) throws Exception {
        Object raw = body.get("orderId") != null ? body.get("orderId") : body.get("order_id");
        try { return Long.parseLong(Objects.toString(raw, "")); } catch (NumberFormatException ex) { throw new Exception("Order not found"); }
    }
}
