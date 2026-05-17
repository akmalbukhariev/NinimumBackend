package com.ninimum.api.recentlyviewedproduct.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.RecentlyViewedProductDto;
import com.ninimum.api.param.AddRecentlyViewedProductParam;
import com.ninimum.api.param.RecentlyViewedProductListParam;
import com.ninimum.api.recentlyviewedproduct.service.IRecentlyViewedProductService;
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
@Tag(name = "Recently Viewed Product", description = "Recently viewed product APIs.")
@RequestMapping(value={"/ninimum/api/v1/recentlyViewedProduct"})
public class RecentlyViewedProductController extends BaseController {

    private final IRecentlyViewedProductService recentlyViewedProductService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Recently Viewed Product"},
            summary = "1. Recently viewed product list",
            description = "Returns recently viewed product list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getRecentlyViewedProductList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getRecentlyViewedProductList(@RequestBody RecentlyViewedProductListParam param) {
        VersionResponseResult result = null;

        try {
            List<RecentlyViewedProductDto> products = this.recentlyViewedProductService.getRecentlyViewedProductList(param);
            result = this.setResult(Result.SUCCESS, products);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("RecentlyViewedProductController => getRecentlyViewedProductList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Recently Viewed Product"},
            summary = "2. Add recently viewed product",
            description = "Adds product to recently viewed list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addRecentlyViewedProduct", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addRecentlyViewedProduct(@RequestBody AddRecentlyViewedProductParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.recentlyViewedProductService.addRecentlyViewedProduct(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("RecentlyViewedProductController => addRecentlyViewedProduct: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}