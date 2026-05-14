package com.ninimum.api.tariff.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.TariffDto;
import com.ninimum.api.param.TariffDetailParam;
import com.ninimum.api.tariff.service.ITariffService;
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
@Tag(name = "Tariff", description = "Subscription tariff APIs.")
@RequestMapping(value={"/samokat/api/v1/tariff"})
public class TariffController extends BaseController {

    private final ITariffService tariffService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Tariff"},
            summary = "1. Tariff list",
            description = "Returns available subscription tariff list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getTariffList")
    public ResponseEntity<Object> getTariffList() {
        VersionResponseResult result = null;

        try {
            List<TariffDto> tariffs = this.tariffService.getTariffList();
            result = this.setResult(Result.SUCCESS, tariffs);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("TariffController => getTariffList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Tariff"},
            summary = "2. Tariff detail",
            description = "Returns tariff detail by tariff ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getTariffDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getTariffDetail(@RequestBody TariffDetailParam param) {
        VersionResponseResult result = null;

        try {
            TariffDto tariff = this.tariffService.getTariffDetail(param);
            result = this.setResult(Result.SUCCESS, tariff);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("TariffController => getTariffDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}