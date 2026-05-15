package com.ninimum.api.product.service;

import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMapper {
    List<ProductCategoryDto> getProductCategoryList() throws Exception;
    List<ProductDto> getProductList(ProductListParam param) throws Exception;
    ProductDto getProductDetail(ProductDetailParam param) throws Exception;
    List<ProductDto> searchProductList(SearchProductParam param) throws Exception;
    List<ProductDto> getRecommendedProductList(ProductRecommendParam param) throws Exception;
    List<ProductDto> getPopularProductList() throws Exception;
    ProductCategoryDto getProductCategoryDetail(ProductCategoryDetailParam param) throws Exception;
    List<ProductDto> getProductFilterList(ProductFilterParam param) throws Exception;
}