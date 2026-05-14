package com.ninimum.api.cart.service;

import com.ninimum.api.dto.CartDto;
import com.ninimum.api.param.AddCartParam;
import com.ninimum.api.param.CartListParam;
import com.ninimum.api.param.DeleteCartParam;
import com.ninimum.api.param.UpdateCartParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CartService implements ICartService {

    private final CartMapper cartMapper;

    @Override
    public List<CartDto> getCartList(CartListParam param) throws Exception {
        return this.cartMapper.getCartList(param);
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
    public int deleteCart(DeleteCartParam param) throws Exception {
        return this.cartMapper.deleteCart(param);
    }
}