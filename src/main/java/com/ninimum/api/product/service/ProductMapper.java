package com.ninimum.api.product.service;

import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.param.ProductListParam;
import org.apache.ibatis.annotations.Mapper;
import com.ninimum.api.param.ProductDetailParam;

import java.util.List;

@Mapper
public interface ProductMapper {
    List<ProductCategoryDto> getProductCategoryList() throws Exception;
    List<ProductDto> getProductList(ProductListParam param) throws Exception;
    ProductDto getProductDetail(ProductDetailParam param) throws Exception;
}