package com.ninimum.api.file.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.FileUploadDto;
import com.ninimum.api.file.service.IFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "File", description = "File upload APIs.")
@RequestMapping(value={"/ninimum/api/v1/file"})
public class FileController extends BaseController {

    private final IFileService fileService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"File"},
            summary = "1. Upload file",
            description = "Uploads a file and returns file URL.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/uploadFile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadFile(@RequestParam("file") MultipartFile file) {
        VersionResponseResult result = null;

        try {
            FileUploadDto uploadedFile = this.fileService.uploadFile(file);
            result = this.setResult(Result.SUCCESS, uploadedFile);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("FileController => uploadFile: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}