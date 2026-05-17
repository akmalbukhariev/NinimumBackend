package com.ninimum.api.banner.controller;

import com.ninimum.api.banner.service.IBannerService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.BannerDto;
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
@Tag(name = "Banner", description = "Main banner APIs.")
@RequestMapping(value={"/ninimum/api/v1/banner"})
public class BannerController extends BaseController {

    private final IBannerService bannerService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Banner"},
            summary = "1. Banner list",
            description = "Returns active banner list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getBannerList")
    public ResponseEntity<Object> getBannerList() {
        VersionResponseResult result = null;

        try {
            List<BannerDto> banners = this.bannerService.getBannerList();
            result = this.setResult(Result.SUCCESS, banners);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("BannerController => getBannerList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}