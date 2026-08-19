package com.ninimum.api.payment.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.payme.CreatePaymeCheckoutUrlParam;
import com.ninimum.api.dto.payme.PaymeRequest;
import com.ninimum.api.dto.payme.PaymeResponse;
import com.ninimum.api.payment.service.IPaymeService;
import com.ninimum.api.response.payme.CreatePaymeCheckoutUrlResponse;
import com.ninimum.api.common.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Payme", description = "Payme Merchant API callbacks.")
@RequestMapping(value = {"/ninimum/api/v1/payment/payme"})
public class PaymeController extends BaseController {

    private final IPaymeService paymeService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Payme"},
            summary = "1. Payme callback",
            description = "Handles Payme Merchant API JSON-RPC callback.",
            responses = {@ApiResponse(responseCode = "200", description = "success")},
            security = {@SecurityRequirement(name = "paymeBasicAuth") }
    )
    @PostMapping(value = "/callback", headers = {"Content-type=application/json"})
    public ResponseEntity<PaymeResponse> callback(@RequestHeader(value = "Authorization",required = false) String authorization, @RequestBody PaymeRequest request) {
        try {
            if (!paymeService.isValidAuthorization(authorization)) {
                return ResponseEntity.ok(
                        PaymeResponse.error(
                                -32504,
                                "Invalid authorization",
                                null,
                                request != null ? request.getId() : null
                        )
                );
            }

            return ResponseEntity.ok(paymeService.handleRequest(request));
        } catch (Exception ex) {
            log.error("PaymeController => callback: ", ex);

            return ResponseEntity.ok(
                    PaymeResponse.error(
                            -32400,
                            "System error",
                            null,
                            request != null ? request.getId() : null
                    )
            );
        }
    }

    @Operation(
            tags = {"Payme"},
            summary = "2. Create Payme checkout URL",
            description = "Creates Payme checkout payment URL for MAUI app. The app opens this URL to show Payme payment UI.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Checkout URL created successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid order or checkout URL creation failed")
            }
    )
    @PostMapping(value = "/createCheckoutUrl", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createCheckoutUrl(@RequestBody CreatePaymeCheckoutUrlParam param) {
        VersionResponseResult result;

        try {
            CreatePaymeCheckoutUrlResponse checkoutUrl = paymeService.createCheckoutUrl(param);

            result = setResult(Result.SUCCESS, checkoutUrl);
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);

            log.error("PaymeController => createCheckoutUrl: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}