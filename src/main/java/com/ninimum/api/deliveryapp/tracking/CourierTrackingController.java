package com.ninimum.api.deliveryapp.tracking;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Courier GPS V1b.
 * Courier app: POST delivery-app/locations {points:[{recorded_at(ms or ISO),latitude,longitude,accuracy,speed,battery,mock}]}.
 * Web admin: live map and route replay (delivery.view / orders.view through StaffAccess path rules).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ninimum/api/v1")
public class CourierTrackingController extends BaseController {
    private final CourierTracking tracking;

    @SuppressWarnings("unchecked")
    @PostMapping("/delivery-app/locations") public ResponseEntity<Object> upload(Authentication auth, @RequestBody Map<String, Object> body) {
        return run(() -> {
            Object raw = body.get("points");
            var points = new ArrayList<Map<String, Object>>();
            if (raw instanceof List<?> list) for (Object o : list) if (o instanceof Map<?, ?> m) points.add((Map<String, Object>) m);
            return Map.of("saved", tracking.save(auth.getName(), points));
        });
    }

    @GetMapping("/admin/management/delivery/tracking/live") public ResponseEntity<Object> live() { return run(tracking::live); }

    @GetMapping("/admin/management/delivery/tracking/day") public ResponseEntity<Object> day(@RequestParam("worker_id") long worker, @RequestParam String date) {
        return run(() -> tracking.day(worker, LocalDate.parse(date)));
    }

    @GetMapping("/admin/management/orders/{id}/route") public ResponseEntity<Object> order(@PathVariable long id) { return run(() -> tracking.order(id)); }

    private interface Action { Object run() throws Exception; }
    private ResponseEntity<Object> run(Action action) {
        VersionResponseResult result;
        try { result = setResult(Result.SUCCESS, action.run()); }
        catch (Exception ex) { log.warn("CourierTrackingController: {}", ex.getMessage()); result = setResult(Result.FAILED); result.setResultMsg(ex.getMessage()); }
        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}
