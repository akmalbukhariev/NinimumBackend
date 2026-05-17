package com.ninimum.api.partner.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.PartnerDto;
import com.ninimum.api.param.PartnerDetailParam;
import com.ninimum.api.partner.service.IPartnerService;
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
@Tag(name = "Partner", description = "Partner supermarket APIs.")
@RequestMapping(value={"/ninimum/api/v1/partner"})
public class PartnerController extends BaseController {

    private final IPartnerService partnerService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Partner"},
            summary = "1. Partner list",
            description = "Returns active partner supermarket list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getPartnerList")
    public ResponseEntity<Object> getPartnerList() {
        VersionResponseResult result = null;

        try {
            List<PartnerDto> partners = this.partnerService.getPartnerList();
            result = this.setResult(Result.SUCCESS, partners);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PartnerController => getPartnerList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Partner"},
            summary = "2. Partner detail",
            description = "Returns partner detail by partner ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getPartnerDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getPartnerDetail(@RequestBody PartnerDetailParam param) {
        VersionResponseResult result = null;

        try {
            PartnerDto partner = this.partnerService.getPartnerDetail(param);
            result = this.setResult(Result.SUCCESS, partner);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PartnerController => getPartnerDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}