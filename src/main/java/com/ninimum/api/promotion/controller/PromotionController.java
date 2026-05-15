package com.ninimum.api.promotion.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.PromotionDto;
import com.ninimum.api.param.PromotionDetailParam;
import com.ninimum.api.promotion.service.IPromotionService;
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
@Tag(name = "Promotion", description = "Promotion APIs.")
@RequestMapping(value={"/samokat/api/v1/promotion"})
public class PromotionController extends BaseController {

    private final IPromotionService promotionService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Promotion"},
            summary = "1. Promotion list",
            description = "Returns active promotion list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getPromotionList")
    public ResponseEntity<Object> getPromotionList() {
        VersionResponseResult result = null;

        try {
            List<PromotionDto> promotions = this.promotionService.getPromotionList();
            result = this.setResult(Result.SUCCESS, promotions);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PromotionController => getPromotionList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Promotion"},
            summary = "2. Promotion detail",
            description = "Returns promotion detail by promotion ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getPromotionDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getPromotionDetail(@RequestBody PromotionDetailParam param) {
        VersionResponseResult result = null;

        try {
            PromotionDto promotion = this.promotionService.getPromotionDetail(param);
            result = this.setResult(Result.SUCCESS, promotion);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("PromotionController => getPromotionDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}