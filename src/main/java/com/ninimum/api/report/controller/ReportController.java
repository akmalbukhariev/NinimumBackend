package com.ninimum.api.report.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.ReportDto;
import com.ninimum.api.param.AddReportParam;
import com.ninimum.api.param.ReportListParam;
import com.ninimum.api.report.service.IReportService;
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
@Tag(name = "Report", description = "User report APIs.")
@RequestMapping(value={"/samokat/api/v1/report"})
public class ReportController extends BaseController {

    private final IReportService reportService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Report"},
            summary = "1. Add report",
            description = "Adds a new user report.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addReport", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addReport(@RequestBody AddReportParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.reportService.addReport(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReportController => addReport: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Report"},
            summary = "2. My report list",
            description = "Returns report list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getReportList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getReportList(@RequestBody ReportListParam param) {
        VersionResponseResult result = null;

        try {
            List<ReportDto> reports = this.reportService.getReportList(param);
            result = this.setResult(Result.SUCCESS, reports);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ReportController => getReportList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}