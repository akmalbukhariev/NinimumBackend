package com.ninimum.api.product.service;

import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.param.*;
import com.ninimum.api.response.ProductListResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IProductService {
    int createProduct(AddProductParam param, List<MultipartFile> images) throws Exception;
    List<ProductCategoryDto> getProductCategoryList() throws Exception;

    List<ProductListResponse> getProductList(ProductListParam param) throws Exception;

    ProductDto getProductDetail(ProductDetailParam param) throws Exception;

    List<ProductListResponse> searchProductList(SearchProductParam param) throws Exception;
    List<ProductDto> getRecommendedProductList(ProductRecommendParam param) throws Exception;
    List<ProductDto> getPopularProductList() throws Exception;
    ProductCategoryDto getProductCategoryDetail(ProductCategoryDetailParam param) throws Exception;
    List<ProductDto> getProductFilterList(ProductFilterParam param) throws Exception;
}