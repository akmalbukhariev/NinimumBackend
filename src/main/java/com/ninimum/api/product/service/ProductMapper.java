package com.ninimum.api.product.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.dto.ProductImageDto;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper {
    int insertProduct(AddProductParam param) throws Exception;
    Long getLastInsertId() throws Exception;
    List<CamelCaseMap> getProductImages(Long productId) throws Exception;
    int insertProductImages(AddProductParam param) throws Exception;
    List<ProductCategoryDto> getProductCategoryList() throws Exception;
    List<CamelCaseMap> getProductList(ProductListParam param) throws Exception;
    CamelCaseMap getProductDetail(ProductDetailParam param) throws Exception;
    List<CamelCaseMap> getSimilarProductList(SimilarProductListParam param) throws Exception;
    List<CamelCaseMap> searchProductList(SearchProductParam param) throws Exception;
    List<ProductDto> getRecommendedProductList(ProductRecommendParam param) throws Exception;
    List<ProductDto> getPopularProductList() throws Exception;
    ProductCategoryDto getProductCategoryDetail(ProductCategoryDetailParam param) throws Exception;
    List<ProductDto> getProductFilterList(ProductFilterParam param) throws Exception;
}