package com.ninimum.api.appconfig.controller;

import com.ninimum.api.appconfig.service.IAppConfigService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.AppConfigDto;
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
@Tag(name = "App Config", description = "Application configuration APIs.")
@RequestMapping(value={"/ninimum/api/v1/appConfig"})
public class AppConfigController extends BaseController {

    private final IAppConfigService appConfigService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"App Config"},
            summary = "1. App config",
            description = "Returns application configuration information.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getAppConfig")
    public ResponseEntity<Object> getAppConfig() {
        VersionResponseResult result = null;

        try {
            AppConfigDto appConfig = this.appConfigService.getAppConfig();
            result = this.setResult(Result.SUCCESS, appConfig);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AppConfigController => getAppConfig: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}