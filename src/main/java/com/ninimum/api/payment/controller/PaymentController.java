package com.ninimum.api.payment.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.PaymentDto;
import com.ninimum.api.param.CancelPaymentParam;
import com.ninimum.api.param.CreatePaymentParam;
import com.ninimum.api.param.PaymentDetailParam;
import com.ninimum.api.param.PaymentListParam;
import com.ninimum.api.payment.service.IPaymentService;
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
@Tag(name = "Payment", description = "Payment APIs.")
@RequestMapping(value={"/ninimum/api/v1/payment"})
public class PaymentController extends BaseController {

    private final IPaymentService paymentService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Payment"},
            summary = "1. Payment list",
            description = "Returns payment list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getPaymentList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getPaymentList(@RequestBody PaymentListParam param) {
        VersionResponseResult result = null;

        try {
            List<PaymentDto> payments = this.paymentService.getPaymentList(param);
            result = this.setResult(Result.SUCCESS, payments);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PaymentController => getPaymentList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payment"},
            summary = "2. Payment detail",
            description = "Returns payment detail by payment ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getPaymentDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getPaymentDetail(@RequestBody PaymentDetailParam param) {
        VersionResponseResult result = null;

        try {
            PaymentDto payment = this.paymentService.getPaymentDetail(param);
            result = this.setResult(Result.SUCCESS, payment);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PaymentController => getPaymentDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payment"},
            summary = "3. Create payment",
            description = "Creates payment information for an order.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createPayment", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createPayment(@RequestBody CreatePaymentParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.paymentService.createPayment(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PaymentController => createPayment: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payment"},
            summary = "4. Cancel payment",
            description = "Cancels payment by payment ID and user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/cancelPayment", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> cancelPayment(@RequestBody CancelPaymentParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.paymentService.cancelPayment(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PaymentController => cancelPayment: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}