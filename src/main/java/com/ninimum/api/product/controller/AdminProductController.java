package com.ninimum.api.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.param.AddProductParam;
import com.ninimum.api.product.service.IProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Admin Product", description = "Admin product management APIs.")
@RequestMapping(value={"/ninimum/api/v1/admin/product"})
public class AdminProductController extends BaseController {

    private final IProductService productService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Admin Product"},
            summary = "1. Create product",
            description = "Admin creates a new product.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createProduct", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> createProduct(
            @RequestParam("data") String data,
            @RequestParam(value = "images", required = false) List<MultipartFile> images
    ){
        VersionResponseResult result = null;

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            AddProductParam param = objectMapper.readValue(data, AddProductParam.class);

            int resultNum = this.productService.createProduct(param, images);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminProductController => createProduct: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}