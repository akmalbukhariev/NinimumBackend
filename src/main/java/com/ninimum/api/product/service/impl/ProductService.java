package com.ninimum.api.product.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Converter;
import com.ninimum.api.dto.ProductCategoryDto;
import com.ninimum.api.dto.ProductDto;
import com.ninimum.api.dto.ProductImageDto;
import com.ninimum.api.file.service.impl.FileService;
import com.ninimum.api.param.*;
import com.ninimum.api.product.service.IProductService;
import com.ninimum.api.product.service.ProductMapper;
import com.ninimum.api.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService implements IProductService {

    private final ProductMapper productMapper;
    private final FileService fileService;

    @Value("${file.access.url}")
    private String fileAccessUrl;

    @Override
    @Transactional
    public int createProduct(AddProductParam param, List<MultipartFile> images) throws Exception {
        int productResult = productMapper.insertProduct(param);

        if (productResult == 0) {
            return 0;
        }

        Long productId = productMapper.getLastInsertId();
        param.setId(productId);

        if (productId == null) {
            return 0;
        }

        if (images != null && !images.isEmpty()) {

            List<ProductImageParam> imageParams = new ArrayList<>();

            for (int i = 0; i < images.size(); i++) {
                String savedFileName = fileService.saveProductImage(images.get(i));

                ProductImageParam imageParam = new ProductImageParam();
                imageParam.setImage_url(savedFileName);
                imageParam.setSort_order(i + 1);

                imageParams.add(imageParam);
            }

            param.setImages(imageParams);
            productMapper.insertProductImages(param);
        }

        return productResult;
    }

    @Override
    public List<ProductCategoryDto> getProductCategoryList() throws Exception {
        return this.productMapper.getProductCategoryList();
    }

    @Override
    public List<ProductResponse> getProductList(ProductListParam param) throws Exception {

        if (param.getPageSize() <= 0) {
            param.setPageSize(10);
        }

        if (param.getOffset() < 0) {
            param.setOffset(0);
        }

        List<CamelCaseMap> camProducts = this.productMapper.getProductList(param);
        List<ProductResponse> products = Converter.mapToDtoList(camProducts, ProductResponse.class);

        for (ProductResponse product : products) {

            List<CamelCaseMap> mapList = this.productMapper.getProductImages(product.getId());
            List<ProductImageDto> images = Converter.mapToDtoList(mapList, ProductImageDto.class);

            for (ProductImageDto image : images) {
                if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                    image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
                }
            }

            product.setImages(images);
        }

        return products;
    }

    @Override
    public ProductResponse getProductDetail(ProductDetailParam param) throws Exception {

        CamelCaseMap camProduct = this.productMapper.getProductDetail(param);

        if (camProduct == null) {
            return null;
        }

        ProductResponse product = camProduct.toObject(ProductResponse.class);

        List<CamelCaseMap> mapList = this.productMapper.getProductImages(product.getId());
        List<ProductImageDto> images = Converter.mapToDtoList(mapList, ProductImageDto.class);

        for (ProductImageDto image : images) {
            if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
            }
        }

        product.setImages(images);

        return product;
    }

    @Override
    public List<ProductResponse> getSimilarProductList(SimilarProductListParam param) throws Exception {

        if (param.getPageSize() <= 0) {
            param.setPageSize(10);
        }

        if (param.getOffset() < 0) {
            param.setOffset(0);
        }

        List<CamelCaseMap> camProducts = this.productMapper.getSimilarProductList(param);
        List<ProductResponse> products = Converter.mapToDtoList(camProducts, ProductResponse.class);

        for (ProductResponse product : products) {

            List<CamelCaseMap> mapList = this.productMapper.getProductImages(product.getId());
            List<ProductImageDto> images = Converter.mapToDtoList(mapList, ProductImageDto.class);

            for (ProductImageDto image : images) {
                if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                    image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
                }
            }

            product.setImages(images);
        }

        return products;
    }

    @Override
    public List<ProductResponse> searchProductList(SearchProductParam param) throws Exception {

        List<CamelCaseMap> camProducts = this.productMapper.searchProductList(param);
        List<ProductResponse> products =
                Converter.mapToDtoList(camProducts, ProductResponse.class);

        for (ProductResponse product : products) {

            List<CamelCaseMap> mapList = this.productMapper.getProductImages(product.getId());
            List<ProductImageDto> images =
                    Converter.mapToDtoList(mapList, ProductImageDto.class);

            for (ProductImageDto image : images) {
                if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                    image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
                }
            }

            product.setImages(images);
        }

        return products;
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