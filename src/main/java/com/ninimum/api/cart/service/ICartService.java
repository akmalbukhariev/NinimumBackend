package com.ninimum.api.cart.service;

import com.ninimum.api.dto.CartCountDto;
import com.ninimum.api.dto.CartResponse;
import com.ninimum.api.param.*;

import java.util.List;

public interface ICartService {
    List<CartResponse> getCartList(CartListParam param) throws Exception;

    int addCart(AddCartParam param) throws Exception;

    int updateCart(UpdateCartParam param) throws Exception;

    int deleteCart(DeletePaymentCardParam param) throws Exception;
    int clearCart(ClearCartParam param) throws Exception;
    CartCountDto getCartCount(CartListParam param) throws Exception;
}