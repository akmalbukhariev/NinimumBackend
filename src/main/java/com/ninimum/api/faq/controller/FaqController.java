package com.ninimum.api.faq.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.FaqDto;
import com.ninimum.api.faq.service.IFaqService;
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
@Tag(name = "FAQ", description = "FAQ APIs.")
@RequestMapping(value={"/samokat/api/v1/faq"})
public class FaqController extends BaseController {

    private final IFaqService faqService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"FAQ"},
            summary = "1. FAQ list",
            description = "Returns active FAQ list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getFaqList")
    public ResponseEntity<Object> getFaqList() {
        VersionResponseResult result = null;

        try {
            List<FaqDto> faqs = this.faqService.getFaqList();
            result = this.setResult(Result.SUCCESS, faqs);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("FaqController => getFaqList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}