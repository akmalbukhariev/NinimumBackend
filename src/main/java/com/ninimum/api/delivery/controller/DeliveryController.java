package com.ninimum.api.delivery.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.delivery.service.IDeliveryService;
import com.ninimum.api.dto.DeliveryDto;
import com.ninimum.api.param.DeliveryDetailParam;
import com.ninimum.api.param.DeliveryListParam;
import com.ninimum.api.param.UpdateDeliveryStatusParam;
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
@Tag(name = "Delivery", description = "Delivery APIs.")
@RequestMapping(value={"/samokat/api/v1/delivery"})
public class DeliveryController extends BaseController {

    private final IDeliveryService deliveryService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Delivery"},
            summary = "1. Delivery list",
            description = "Returns delivery list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getDeliveryList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getDeliveryList(@RequestBody DeliveryListParam param) {
        VersionResponseResult result = null;

        try {
            List<DeliveryDto> deliveries = this.deliveryService.getDeliveryList(param);
            result = this.setResult(Result.SUCCESS, deliveries);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("DeliveryController => getDeliveryList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Delivery"},
            summary = "2. Delivery detail",
            description = "Returns delivery detail by delivery ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getDeliveryDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getDeliveryDetail(@RequestBody DeliveryDetailParam param) {
        VersionResponseResult result = null;

        try {
            DeliveryDto delivery = this.deliveryService.getDeliveryDetail(param);
            result = this.setResult(Result.SUCCESS, delivery);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("DeliveryController => getDeliveryDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Delivery"},
            summary = "3. Update delivery status",
            description = "Updates delivery status.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/updateDeliveryStatus", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> updateDeliveryStatus(@RequestBody UpdateDeliveryStatusParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.deliveryService.updateDeliveryStatus(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("DeliveryController => updateDeliveryStatus: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}