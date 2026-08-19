package com.ninimum.api.payment.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.payme.subscribe.PaymeCreateCardParam;
import com.ninimum.api.dto.payme.subscribe.PaymeVerifyCardParam;
import com.ninimum.api.payment.service.IPaymeSubscribeService;
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
@Tag(name = "Payme Subscribe", description = "Payme Subscribe API.")
@RequestMapping(value = {"/ninimum/api/v1/payment/payme/subscribe"})
public class PaymeSubscribeController extends BaseController {

    private final IPaymeSubscribeService paymeSubscribeService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Payme Subscribe"},
            summary = "1. Create Payme card token",
            description = "Creates Payme card token using card number and expiry date. This is the first step for saving a customer card.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/cards/create", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createCard(@RequestBody PaymeCreateCardParam param) {
        VersionResponseResult result;

        try {
            result = setResult(Result.SUCCESS, paymeSubscribeService.createCard(param));
        } catch (Exception ex) {
            log.error("PaymeSubscribeController => createCard: ", ex);
            result = setResult(Result.SERVER_ERROR);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payme Subscribe"},
            summary = "2. Get Payme card verify code",
            description = "Requests SMS verification code for the created Payme card token.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/cards/getVerifyCode", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getVerifyCode(@RequestParam String token) {
        VersionResponseResult result;

        try {
            result = setResult(Result.SUCCESS, paymeSubscribeService.getVerifyCode(token));
        } catch (Exception ex) {
            log.error("PaymeSubscribeController => getVerifyCode: ", ex);
            result = setResult(Result.SERVER_ERROR);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payme Subscribe"},
            summary = "3. Verify Payme card",
            description = "Verifies Payme card token using SMS verification code. After success, the token can be saved and reused for future payments.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/cards/verify", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> verifyCard(@RequestBody PaymeVerifyCardParam param) {
        VersionResponseResult result;

        try {
            result = setResult(Result.SUCCESS, paymeSubscribeService.verifyCard(param));
        } catch (Exception ex) {
            log.error("PaymeSubscribeController => verifyCard: ", ex);
            result = setResult(Result.SERVER_ERROR);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}