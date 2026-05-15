package com.ninimum.api.product.service.impl;

import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.param.*;
import com.ninimum.api.product.service.IProductService;
import com.ninimum.api.product.service.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService {

    private final ProductMapper productMapper;

    @Override
    public List<ProductCategoryDto> getProductCategoryList() throws Exception {
        return this.productMapper.getProductCategoryList();
    }

    @Override
    public List<ProductDto> getProductList(ProductListParam param) throws Exception {
        return this.productMapper.getProductList(param);
    }

    @Override
    public ProductDto getProductDetail(ProductDetailParam param) throws Exception {
        return this.productMapper.getProductDetail(param);
    }

    @Override
    public List<ProductDto> searchProductList(SearchProductParam param) throws Exception {
        return this.productMapper.searchProductList(param);
    }

    @Override
    public List<ProductDto> getRecommendedProductList(ProductRecommendParam param) throws Exception {
        return this.productMapper.getRecommendedProductList(param);
    }

    @Override
    public List<ProductDto> getPopularProductList() throws Exception {
        return this.productMapper.getPopularProductList();
    }

    @Override
    public ProductCategoryDto getProductCategoryDetail(ProductCategoryDetailParam param) throws Exception {
        return this.productMapper.getProductCategoryDetail(param);
    }

    @Override
    public List<ProductDto> getProductFilterList(ProductFilterParam param) throws Exception {
        return this.productMapper.getProductFilterList(param);
    }
}