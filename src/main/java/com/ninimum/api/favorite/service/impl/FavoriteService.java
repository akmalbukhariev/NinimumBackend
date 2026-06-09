package com.ninimum.api.favorite.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.common.Converter;
import com.ninimum.api.dto.FavoriteCountDto;
import com.ninimum.api.dto.ProductImageDto;
import com.ninimum.api.favorite.service.FavoriteMapper;
import com.ninimum.api.favorite.service.IFavoriteService;
import com.ninimum.api.param.AddFavoriteParam;
import com.ninimum.api.param.DeleteFavoriteParam;
import com.ninimum.api.param.FavoriteListParam;
import com.ninimum.api.product.service.ProductMapper;
import com.ninimum.api.response.ProductResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService implements IFavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;

    @Value("${file.access.url}")
    private String fileAccessUrl;

    @Override
    public List<ProductResponse> getFavoriteList(FavoriteListParam param) throws Exception {

        List<CamelCaseMap> camProducts = this.favoriteMapper.getFavoriteList(param);
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

            product.setLiked(true);
            product.setImages(images);
        }

        return products;
    }

    @Override
    public int addFavorite(AddFavoriteParam param) throws Exception {
        return this.favoriteMapper.addFavorite(param);
    }

    @Override
    public int deleteFavorite(DeleteFavoriteParam param) throws Exception {
        return this.favoriteMapper.deleteFavorite(param);
    }

    @Override
    public FavoriteCountDto getFavoriteCount(FavoriteListParam param) throws Exception {
        int count = this.favoriteMapper.getFavoriteCount(param);

        FavoriteCountDto dto = new FavoriteCountDto();
        dto.setFavoriteCount(count);

        return dto;
    }
}