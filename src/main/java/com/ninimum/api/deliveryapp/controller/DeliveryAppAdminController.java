package com.ninimum.api.deliveryapp.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.deliveryapp.service.IDeliveryAppService;
import com.ninimum.api.param.CreateDeliveryAppWorkerParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Delivery App Admin", description = "Temporary Swagger/admin APIs for courier accounts")
@RequestMapping("/ninimum/api/v1/delivery-app/admin")
public class DeliveryAppAdminController extends BaseController {

    private final IDeliveryAppService service;

    @PostConstruct
    public void init() { setApiVersion(Constant.api_version); }

    @Operation(summary = "Create courier account", security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping("/createWorker")
    public ResponseEntity<Object> createWorker(@RequestBody CreateDeliveryAppWorkerParam param) {
        try {
            return new ResponseEntity<>(setResult(Result.SUCCESS, service.createWorker(param)), HttpStatus.OK);
        } catch (Exception ex) {
            log.error("createWorker", ex);
            VersionResponseResult result = setResult(Result.SERVER_ERROR);
            result.setResultMsg(ex.getMessage());
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
    }

    @Operation(summary = "Courier list", security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping("/workers")
    public ResponseEntity<Object> workers() {
        try {
            return new ResponseEntity<>(setResult(Result.SUCCESS, service.getWorkers()), HttpStatus.OK);
        } catch (Exception ex) {
            return new ResponseEntity<>(setResult(Result.SERVER_ERROR), HttpStatus.OK);
        }
    }
}
