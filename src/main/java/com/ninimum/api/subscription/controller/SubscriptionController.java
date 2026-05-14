package com.ninimum.api.subscription.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.SubscriptionDto;
import com.ninimum.api.param.ActiveSubscriptionParam;
import com.ninimum.api.param.CreateSubscriptionParam;
import com.ninimum.api.param.SubscriptionListParam;
import com.ninimum.api.subscription.service.ISubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "User subscription APIs.")
@RequestMapping(value={"/samokat/api/v1/subscription"})
public class SubscriptionController extends BaseController {

    private final ISubscriptionService subscriptionService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Subscription"},
            summary = "1. Subscription list",
            description = "Returns subscription history by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getSubscriptionList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getSubscriptionList(@RequestBody SubscriptionListParam param) {
        VersionResponseResult result = null;

        try {
            List<SubscriptionDto> subscriptions = this.subscriptionService.getSubscriptionList(param);
            result = this.setResult(Result.SUCCESS, subscriptions);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("SubscriptionController => getSubscriptionList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Subscription"},
            summary = "2. Active subscription",
            description = "Returns active subscription by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getActiveSubscription", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getActiveSubscription(@RequestBody ActiveSubscriptionParam param) {
        VersionResponseResult result = null;

        try {
            SubscriptionDto subscription = this.subscriptionService.getActiveSubscription(param);
            result = this.setResult(Result.SUCCESS, subscription);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("SubscriptionController => getActiveSubscription: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Subscription"},
            summary = "3. Create subscription",
            description = "Creates a new subscription for user.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createSubscription", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createSubscription(@RequestBody CreateSubscriptionParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.subscriptionService.createSubscription(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("SubscriptionController => createSubscription: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}