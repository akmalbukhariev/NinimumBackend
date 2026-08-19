package com.ninimum.api.product.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.dto.payme.FiscalMxikPackageListParam;
import com.ninimum.api.param.*;
import com.ninimum.api.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IProductService {
    int createProduct(AddProductParam param, List<MultipartFile> images) throws Exception;
    List<CamelCaseMap> getFiscalMxikPackageList(FiscalMxikPackageListParam param) throws Exception;
    List<ProductCategoryDto> getProductCategoryList() throws Exception;
    List<ProductResponse> getProductList(ProductListParam param) throws Exception;
    ProductResponse getProductDetail(ProductDetailParam param) throws Exception;
    List<ProductResponse> getSimilarProductList(SimilarProductListParam param) throws Exception;
    List<ProductResponse> searchProductList(SearchProductParam param) throws Exception;
    List<ProductDto> getRecommendedProductList(ProductRecommendParam param) throws Exception;
    List<ProductDto> getPopularProductList() throws Exception;
    ProductCategoryDto getProductCategoryDetail(ProductCategoryDetailParam param) throws Exception;
    List<ProductDto> getProductFilterList(ProductFilterParam param) throws Exception;
}