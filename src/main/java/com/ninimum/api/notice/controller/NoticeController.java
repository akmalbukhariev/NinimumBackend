package com.ninimum.api.notice.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.NoticeDto;
import com.ninimum.api.notice.service.INoticeService;
import com.ninimum.api.param.NoticeDetailParam;
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
@Tag(name = "Notice", description = "Notice APIs.")
@RequestMapping(value={"/ninimum/api/v1/notice"})
public class NoticeController extends BaseController {

    private final INoticeService noticeService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Notice"},
            summary = "1. Notice list",
            description = "Returns active notice list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getNoticeList")
    public ResponseEntity<Object> getNoticeList() {
        VersionResponseResult result = null;

        try {
            List<NoticeDto> notices = this.noticeService.getNoticeList();
            result = this.setResult(Result.SUCCESS, notices);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("NoticeController => getNoticeList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Notice"},
            summary = "2. Notice detail",
            description = "Returns notice detail by notice ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getNoticeDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getNoticeDetail(@RequestBody NoticeDetailParam param) {
        VersionResponseResult result = null;

        try {
            NoticeDto notice = this.noticeService.getNoticeDetail(param);
            result = this.setResult(Result.SUCCESS, notice);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("NoticeController => getNoticeDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}