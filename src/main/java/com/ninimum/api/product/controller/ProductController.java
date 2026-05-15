package com.ninimum.api.product.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.param.*;
import com.ninimum.api.product.service.IProductService;
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
@Tag(name = "Product", description = "Product and category APIs.")
@RequestMapping(value={"/samokat/api/v1/product"})
public class ProductController extends BaseController {

    private final IProductService productService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Product"},
            summary = "1. Product category list",
            description = "Returns product category list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getProductCategoryList")
    public ResponseEntity<Object> getProductCategoryList() {
        VersionResponseResult result = null;

        try {
            List<ProductCategoryDto> categories = this.productService.getProductCategoryList();
            result = this.setResult(Result.SUCCESS, categories);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getProductCategoryList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "2. Product list",
            description = "Returns product list by category ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getProductList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getProductList(@RequestBody ProductListParam param) {
        VersionResponseResult result = null;

        try {
            List<ProductDto> products = this.productService.getProductList(param);
            result = this.setResult(Result.SUCCESS, products);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getProductList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "3. Product detail",
            description = "Returns product detail by product ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getProductDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getProductDetail(@RequestBody ProductDetailParam param) {
        VersionResponseResult result = null;

        try {
            ProductDto product = this.productService.getProductDetail(param);
            result = this.setResult(Result.SUCCESS, product);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getProductDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "4. Search product list",
            description = "Searches products by keyword.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/searchProductList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> searchProductList(@RequestBody SearchProductParam param) {
        VersionResponseResult result = null;

        try {
            List<ProductDto> products = this.productService.searchProductList(param);
            result = this.setResult(Result.SUCCESS, products);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => searchProductList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "5. Recommended product list",
            description = "Returns recommended product list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getRecommendedProductList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getRecommendedProductList(@RequestBody ProductRecommendParam param) {
        VersionResponseResult result = null;

        try {
            List<ProductDto> products = this.productService.getRecommendedProductList(param);
            result = this.setResult(Result.SUCCESS, products);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getRecommendedProductList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "6. Popular product list",
            description = "Returns popular product list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getPopularProductList")
    public ResponseEntity<Object> getPopularProductList() {
        VersionResponseResult result = null;

        try {
            List<ProductDto> products = this.productService.getPopularProductList();
            result = this.setResult(Result.SUCCESS, products);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getPopularProductList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "7. Product category detail",
            description = "Returns product category detail by category ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getProductCategoryDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getProductCategoryDetail(@RequestBody ProductCategoryDetailParam param) {
        VersionResponseResult result = null;

        try {
            ProductCategoryDto category = this.productService.getProductCategoryDetail(param);
            result = this.setResult(Result.SUCCESS, category);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getProductCategoryDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Product"},
            summary = "8. Product filter list",
            description = "Returns product list by category, keyword, and price range.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getProductFilterList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getProductFilterList(@RequestBody ProductFilterParam param) {
        VersionResponseResult result = null;

        try {
            List<ProductDto> products = this.productService.getProductFilterList(param);
            result = this.setResult(Result.SUCCESS, products);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("ProductController => getProductFilterList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}