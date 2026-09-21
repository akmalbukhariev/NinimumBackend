package com.ninimum.api.deliveryapp.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.deliveryapp.service.IDeliveryAppService;
import com.ninimum.api.dto.DeliveryAppWorkerDto;
import com.ninimum.api.dto.TokenDto;
import com.ninimum.api.param.DeliveryAppJobParam;
import com.ninimum.api.param.DeliveryAppLoginParam;
import com.ninimum.api.param.DeliveryAppOnlineParam;
import com.ninimum.api.param.DeliveryAppStatusParam;
import com.ninimum.api.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.Collections;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Delivery App", description = "Courier mobile application APIs")
@RequestMapping("/ninimum/api/v1/delivery-app")
public class DeliveryAppController extends BaseController {

    private final IDeliveryAppService service;
    private final JwtTokenProvider jwtTokenProvider;

    @PostConstruct
    public void init() { setApiVersion(Constant.api_version); }

    @Operation(summary = "Courier login")
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody DeliveryAppLoginParam param) {
        try {
            DeliveryAppWorkerDto worker = service.authenticate(param.getWorkerId(), param.getPassword());
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    worker.getWorkerId(), null,
                    Collections.singletonList(new SimpleGrantedAuthority(Constant.ROLE_DELIVERY)));
            TokenDto token = jwtTokenProvider.generateToken(authentication);

            HttpHeaders headers = new HttpHeaders();
            headers.add(Constant.HEADER_ACCESS_TOKEN, token.getAccessToken());
            headers.add(Constant.HEADER_REFRESH_TOKEN, token.getRefreshToken());
            headers.add(Constant.HEADER_ROLE, Constant.ROLE_DELIVERY);
            headers.add(Constant.HEADER_USER_NAME, worker.getFullName());
            return new ResponseEntity<>(setResult(Result.SUCCESS, worker), headers, HttpStatus.OK);
        } catch (Exception ex) {
            log.warn("Delivery app login failed: {}", ex.getMessage());
            return new ResponseEntity<>(setResult(Result.LOGIN_INVALID_TOKEN), HttpStatus.OK);
        }
    }

    @Operation(summary = "Current courier", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/me")
    public ResponseEntity<Object> me(Authentication auth) {
        return execute(() -> service.getWorker(auth.getName()));
    }

    @Operation(summary = "Courier dashboard", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/dashboard")
    public ResponseEntity<Object> dashboard(Authentication auth) {
        return execute(() -> service.getDashboard(auth.getName()));
    }

    @Operation(summary = "Available paid orders", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/available")
    public ResponseEntity<Object> available(Authentication auth) {
        return execute(() -> service.getAvailableJobs(auth.getName()));
    }

    @Operation(summary = "My active deliveries", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/active")
    public ResponseEntity<Object> active(Authentication auth) {
        return execute(() -> service.getActiveJobs(auth.getName()));
    }

    @Operation(summary = "My delivery history", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/history")
    public ResponseEntity<Object> history(Authentication auth) {
        return execute(() -> service.getHistoryJobs(auth.getName()));
    }

    @Operation(summary = "Delivery detail", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/detail")
    public ResponseEntity<Object> detail(Authentication auth, @RequestBody DeliveryAppJobParam param) {
        return execute(() -> service.getJobDetail(auth.getName(), param.getJobId()));
    }

    @Operation(summary = "Claim an available delivery", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/claim")
    public ResponseEntity<Object> claim(Authentication auth, @RequestBody DeliveryAppJobParam param) {
        return execute(() -> { service.claimJob(auth.getName(), param.getJobId()); return null; });
    }

    @Operation(summary = "Update delivery status", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/status")
    public ResponseEntity<Object> status(Authentication auth, @RequestBody DeliveryAppStatusParam param) {
        return execute(() -> { service.updateStatus(auth.getName(), param); return null; });
    }

    @Operation(summary = "Set courier online/offline", security = @SecurityRequirement(name = "bearerAuth"))
    @PutMapping("/online")
    public ResponseEntity<Object> online(Authentication auth, @RequestBody DeliveryAppOnlineParam param) {
        return execute(() -> { service.setOnline(auth.getName(), Boolean.TRUE.equals(param.getOnline())); return null; });
    }

    private ResponseEntity<Object> execute(DeliveryAction action) {
        try {
            Object data = action.run();
            VersionResponseResult result = data == null ? setResult(Result.SUCCESS) : setResult(Result.SUCCESS, data);
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (Exception ex) {
            log.error("DeliveryAppController", ex);
            VersionResponseResult result = setResult(Result.SERVER_ERROR);
            result.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    @FunctionalInterface
    private interface DeliveryAction { Object run() throws Exception; }
}
