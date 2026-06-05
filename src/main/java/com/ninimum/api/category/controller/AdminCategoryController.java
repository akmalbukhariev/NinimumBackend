package com.ninimum.api.category.controller;

import com.ninimum.api.category.service.ICategoryService;
import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.CategoryDto;
import com.ninimum.api.param.AddCategoryParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Admin Category", description = "Admin category management APIs.")
@RequestMapping(value={"/samokat/api/v1/admin/category"})
public class AdminCategoryController extends BaseController {

    private final ICategoryService categoryService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Admin Category"},
            summary = "1. Create category",
            description = "Admin creates a new category or subcategory.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createCategory", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createCategory(@RequestBody AddCategoryParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.categoryService.createCategory(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminCategoryController => createCategory: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Admin Category"},
            summary = "2. Get categories",
            description = "Returns all active categories and subcategories.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getCategories")
    //@PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Object> getCategories() {
        VersionResponseResult result = null;

        try {
            List<CategoryDto> categories = this.categoryService.getCategories();
            result = this.setResult(Result.SUCCESS, categories);

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("AdminCategoryController => getCategories: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}