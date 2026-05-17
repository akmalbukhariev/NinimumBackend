package com.ninimum.api.inquiry.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.InquiryDto;
import com.ninimum.api.inquiry.service.IInquiryService;
import com.ninimum.api.param.AddInquiryParam;
import com.ninimum.api.param.InquiryDetailParam;
import com.ninimum.api.param.InquiryListParam;
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
@Tag(name = "Inquiry", description = "Customer inquiry APIs.")
@RequestMapping(value={"/ninimum/api/v1/inquiry"})
public class InquiryController extends BaseController {

    private final IInquiryService inquiryService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Inquiry"},
            summary = "1. Inquiry list",
            description = "Returns inquiry list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getInquiryList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getInquiryList(@RequestBody InquiryListParam param) {
        VersionResponseResult result = null;

        try {
            List<InquiryDto> inquiries = this.inquiryService.getInquiryList(param);
            result = this.setResult(Result.SUCCESS, inquiries);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("InquiryController => getInquiryList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Inquiry"},
            summary = "2. Inquiry detail",
            description = "Returns inquiry detail by inquiry ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getInquiryDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getInquiryDetail(@RequestBody InquiryDetailParam param) {
        VersionResponseResult result = null;

        try {
            InquiryDto inquiry = this.inquiryService.getInquiryDetail(param);
            result = this.setResult(Result.SUCCESS, inquiry);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("InquiryController => getInquiryDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Inquiry"},
            summary = "3. Add inquiry",
            description = "Adds a new customer inquiry.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addInquiry", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addInquiry(@RequestBody AddInquiryParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.inquiryService.addInquiry(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("InquiryController => addInquiry: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}