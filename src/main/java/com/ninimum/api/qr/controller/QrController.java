package com.ninimum.api.qr.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.QrDto;
import com.ninimum.api.param.CreateQrParam;
import com.ninimum.api.param.QrParam;
import com.ninimum.api.qr.service.IQrService;
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
@Tag(name = "QR", description = "User QR code APIs.")
@RequestMapping(value={"/samokat/api/v1/qr"})
public class QrController extends BaseController {

    private final IQrService qrService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"QR"},
            summary = "1. Get user QR",
            description = "Returns active QR code by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getUserQr", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getUserQr(@RequestBody QrParam param) {
        VersionResponseResult result = null;

        try {
            QrDto qr = this.qrService.getUserQr(param);
            result = this.setResult(Result.SUCCESS, qr);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("QrController => getUserQr: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"QR"},
            summary = "2. Create user QR",
            description = "Creates QR code for user.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createUserQr", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createUserQr(@RequestBody CreateQrParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.qrService.createUserQr(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("QrController => createUserQr: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}