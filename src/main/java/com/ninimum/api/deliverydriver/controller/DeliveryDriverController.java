package com.ninimum.api.deliverydriver.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.deliverydriver.service.IDeliveryDriverService;
import com.ninimum.api.dto.DeliveryDriverDto;
import com.ninimum.api.param.AssignDeliveryDriverParam;
import com.ninimum.api.param.DeliveryDriverDetailParam;
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
@Tag(name = "Delivery Driver", description = "Delivery driver APIs.")
@RequestMapping(value={"/ninimum/api/v1/deliveryDriver"})
public class DeliveryDriverController extends BaseController {

    private final IDeliveryDriverService deliveryDriverService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Delivery Driver"},
            summary = "1. Delivery driver list",
            description = "Returns available delivery driver list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getDeliveryDriverList")
    public ResponseEntity<Object> getDeliveryDriverList() {
        VersionResponseResult result = null;

        try {
            List<DeliveryDriverDto> drivers = this.deliveryDriverService.getDeliveryDriverList();
            result = this.setResult(Result.SUCCESS, drivers);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("DeliveryDriverController => getDeliveryDriverList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Delivery Driver"},
            summary = "2. Delivery driver detail",
            description = "Returns delivery driver detail by driver ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getDeliveryDriverDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getDeliveryDriverDetail(@RequestBody DeliveryDriverDetailParam param) {
        VersionResponseResult result = null;

        try {
            DeliveryDriverDto driver = this.deliveryDriverService.getDeliveryDriverDetail(param);
            result = this.setResult(Result.SUCCESS, driver);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("DeliveryDriverController => getDeliveryDriverDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Delivery Driver"},
            summary = "3. Assign delivery driver",
            description = "Assigns delivery driver to delivery.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PutMapping(value = "/assignDeliveryDriver", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> assignDeliveryDriver(@RequestBody AssignDeliveryDriverParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.deliveryDriverService.assignDeliveryDriver(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("DeliveryDriverController => assignDeliveryDriver: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}