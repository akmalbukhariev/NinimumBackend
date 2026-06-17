package com.ninimum.api.cart.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CartMapper {
    List<CamelCaseMap> getCartList(CartListParam param) throws Exception;

    int addCart(AddCartParam param) throws Exception;

    int updateCart(UpdateCartParam param) throws Exception;

    int deleteCart(DeletePaymentCardParam param) throws Exception;

    int clearCart(ClearCartParam param) throws Exception;
    int getCartCount(CartListParam param) throws Exception;
}