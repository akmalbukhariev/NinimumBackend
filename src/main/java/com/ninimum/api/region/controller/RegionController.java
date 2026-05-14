package com.ninimum.api.region.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.RegionDto;
import com.ninimum.api.region.service.IRegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Region", description = "Stores information about service regions.")
@RequestMapping(value={"/samokat/api/v1/region"})
public class RegionController extends BaseController {

    private final IRegionService regionService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Region"},
            summary = "1. Get regions",
            description = "Returns all active service regions.",
            hidden = false,
            responses = {
                    @ApiResponse(responseCode = "200", description = "success")
            }
    )
    @GetMapping(value = "/getRegions")
    public ResponseEntity<Object> getRegions() {

        VersionResponseResult result = null;

        try {

            List<RegionDto> regions = this.regionService.getActiveRegions();

            result = this.setResult(Result.SUCCESS, regions);

        }
        catch (Exception ex) {

            result = this.setResult(Result.SERVER_ERROR);

            log.error("RegionController => getRegions: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}