package com.ninimum.api.product.service.impl;

import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.param.ProductDetailParam;
import com.ninimum.api.param.ProductListParam;
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
}