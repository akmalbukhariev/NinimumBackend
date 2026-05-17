package com.ninimum.api.terms.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.TermsDto;
import com.ninimum.api.param.TermsDetailParam;
import com.ninimum.api.terms.service.ITermsService;
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
@Tag(name = "Terms", description = "Terms and privacy policy APIs.")
@RequestMapping(value={"/ninimum/api/v1/terms"})
public class TermsController extends BaseController {

    private final ITermsService termsService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Terms"},
            summary = "1. Terms list",
            description = "Returns active terms list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getTermsList")
    public ResponseEntity<Object> getTermsList() {
        VersionResponseResult result = null;

        try {
            List<TermsDto> terms = this.termsService.getTermsList();
            result = this.setResult(Result.SUCCESS, terms);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("TermsController => getTermsList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Terms"},
            summary = "2. Terms detail",
            description = "Returns terms detail by terms ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getTermsDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getTermsDetail(@RequestBody TermsDetailParam param) {
        VersionResponseResult result = null;

        try {
            TermsDto terms = this.termsService.getTermsDetail(param);
            result = this.setResult(Result.SUCCESS, terms);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("TermsController => getTermsDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}