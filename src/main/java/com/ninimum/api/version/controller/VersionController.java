package com.ninimum.api.version.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.VersionCheckDto;
import com.ninimum.api.param.VersionCheckParam;
import com.ninimum.api.version.service.IVersionService;
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
@Tag(name = "Version", description = "App version check APIs.")
@RequestMapping(value={"/ninimum/api/v1/version"})
public class VersionController extends BaseController {

    private final IVersionService versionService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Version"},
            summary = "1. Version check",
            description = "Checks latest app version by OS type.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/checkVersion", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> checkVersion(@RequestBody VersionCheckParam param) {
        VersionResponseResult result = null;

        try {
            VersionCheckDto version = this.versionService.checkVersion(param);
            result = this.setResult(Result.SUCCESS, version);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("VersionController => checkVersion: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}