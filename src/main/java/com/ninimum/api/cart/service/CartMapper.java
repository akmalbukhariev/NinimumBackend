package com.ninimum.api.cart.service;

import com.ninimum.api.dto.CartDto;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CartMapper {
    List<CartDto> getCartList(CartListParam param) throws Exception;

    int addCart(AddCartParam param) throws Exception;

    int updateCart(UpdateCartParam param) throws Exception;

    int deleteCart(DeleteCartParam param) throws Exception;

    int clearCart(ClearCartParam param) throws Exception;
    int getCartCount(CartListParam param) throws Exception;
}