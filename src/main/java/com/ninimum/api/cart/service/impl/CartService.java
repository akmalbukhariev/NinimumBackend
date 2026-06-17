package com.ninimum.api.cart.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.cart.service.CartMapper;
import com.ninimum.api.cart.service.ICartService;
import com.ninimum.api.common.Converter;
import com.ninimum.api.dto.CartCountDto;
import com.ninimum.api.dto.CartResponse;
import com.ninimum.api.dto.ProductImageDto;
import com.ninimum.api.param.*;
import com.ninimum.api.product.service.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService implements ICartService {

    private final ProductMapper productMapper;
    private final CartMapper cartMapper;
    @Value("${file.access.url}")
    private String fileAccessUrl;

    @Override
    public List<CartResponse> getCartList(CartListParam param) throws Exception {

        if (param.getPageSize() <= 0) {
            param.setPageSize(10);
        }

        if (param.getOffset() < 0) {
            param.setOffset(0);
        }

        List<CamelCaseMap> camCartList = this.cartMapper.getCartList(param);
        List<CartResponse> carts = Converter.mapToDtoList(camCartList, CartResponse.class);

        for (CartResponse cart : carts) {

            List<CamelCaseMap> mapList = this.productMapper.getProductImages(cart.getId());
            List<ProductImageDto> images = Converter.mapToDtoList(mapList, ProductImageDto.class);

            for (ProductImageDto image : images) {
                if (image.getImage_url() != null && !image.getImage_url().isEmpty()) {
                    image.setImage_url(fileAccessUrl + "/" + image.getImage_url());
                }
            }

            cart.setImages(images);
        }

        return carts;
    }

    @Override
    public int addCart(AddCartParam param) throws Exception {
        return this.cartMapper.addCart(param);
    }

    @Override
    public int updateCart(UpdateCartParam param) throws Exception {
        return this.cartMapper.updateCart(param);
    }

    @Override
    public int deleteCart(DeletePaymentCardParam param) throws Exception {
        return this.cartMapper.deleteCart(param);
    }

    @Override
    public int clearCart(ClearCartParam param) throws Exception {
        return this.cartMapper.clearCart(param);
    }

    @Override
    public CartCountDto getCartCount(CartListParam param) throws Exception {
        int count = this.cartMapper.getCartCount(param);

        CartCountDto dto = new CartCountDto();
        dto.setCartCount(count);

        return dto;
    }
}