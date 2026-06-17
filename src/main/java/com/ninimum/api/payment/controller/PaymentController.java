package com.ninimum.api.payment.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.PaymentCardDto;
import com.ninimum.api.param.*;
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
@RequestMapping(value = {"/ninimum/api/v1/payment"})
public class PaymentController extends BaseController {

    private final IPaymentService paymentService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Payment"},
            summary = "1. Payment card list",
            description = "Returns payment card list by user ID.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getPaymentCardList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getPaymentCardList(@RequestBody PaymentCardListParam param) {
        VersionResponseResult result;

        try {
            List<PaymentCardDto> cards = paymentService.getPaymentCardList(param);
            result = setResult(Result.SUCCESS, cards);
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("PaymentController => getPaymentCardList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    /*@Operation(
            tags = {"Payment"},
            summary = "2. Payment card detail",
            description = "Returns payment card detail by card ID and user ID.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getPaymentCardDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getPaymentCardDetail(@RequestBody PaymentDetailParam param) {
        VersionResponseResult result;

        try {
            PaymentCardDto card = paymentService.getPaymentDetail(param);
            result = setResult(Result.SUCCESS, card);
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("PaymentController => getPaymentCardDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }*/

    @Operation(
            tags = {"Payment"},
            summary = "3. Create payment card",
            description = "Registers a new payment card. Full card number and CVV are not saved.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createPaymentCard", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createPaymentCard(@RequestBody CreatePaymentCardParam param) {
        VersionResponseResult result;

        try {
            int resultNum = paymentService.createPaymentCard(param);

            if (resultNum != 0) {
                result = setResult(Result.SUCCESS);
            } else {
                result = setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("PaymentController => createPaymentCard: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payment"},
            summary = "4. Delete payment card",
            description = "Deletes payment card by card ID and user ID.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/deletePaymentCard", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> deletePaymentCard(@RequestBody DeletePaymentCardParam param) {
        VersionResponseResult result;

        try {
            int resultNum = paymentService.deletePaymentCard(param);

            if (resultNum != 0) {
                result = setResult(Result.SUCCESS);
            } else {
                result = setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("PaymentController => deletePaymentCard: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payment"},
            summary = "5. Set default payment card",
            description = "Sets selected card as user's default payment card.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/setDefaultPaymentCard", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> setDefaultPaymentCard(@RequestBody SetDefaultPaymentCardParam param) {
        VersionResponseResult result;

        try {
            int resultNum = paymentService.setDefaultPaymentCard(param);

            if (resultNum != 0) {
                result = setResult(Result.SUCCESS);
            } else {
                result = setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("PaymentController => setDefaultPaymentCard: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Payment"},
            summary = "6. Has payment card",
            description = "Checks whether user has at least one registered payment card.",
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/hasPaymentCard", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> hasPaymentCard(@RequestBody PaymentCardListParam param) {
        VersionResponseResult result;

        try {
            int count = paymentService.hasPaymentCard(param);
            result = setResult(Result.SUCCESS, count > 0);
        } catch (Exception ex) {
            result = setResult(Result.SERVER_ERROR);
            log.error("PaymentController => hasPaymentCard: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}