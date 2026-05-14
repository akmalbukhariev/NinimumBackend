package com.ninimum.api.cart.service;

import com.ninimum.api.dto.CartDto;
import com.ninimum.api.param.AddCartParam;
import com.ninimum.api.param.CartListParam;
import com.ninimum.api.param.DeleteCartParam;
import com.ninimum.api.param.UpdateCartParam;

import java.util.List;

public interface ICartService {
    List<CartDto> getCartList(CartListParam param) throws Exception;

    int addCart(AddCartParam param) throws Exception;

    int updateCart(UpdateCartParam param) throws Exception;

    int deleteCart(DeleteCartParam param) throws Exception;
}