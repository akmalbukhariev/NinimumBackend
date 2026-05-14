package com.ninimum.api.order.service;

import com.ninimum.api.dto.OrderDetailDto;
import com.ninimum.api.dto.OrderDto;
import com.ninimum.api.param.CreateOrderParam;
import com.ninimum.api.param.CreateOrderProductParam;
import com.ninimum.api.param.OrderDetailParam;
import com.ninimum.api.param.OrderListParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderMapper orderMapper;

    @Override
    public List<OrderDto> getOrderList(OrderListParam param) throws Exception {
        return this.orderMapper.getOrderList(param);
    }

    @Override
    public List<OrderDetailDto> getOrderDetail(OrderDetailParam param) throws Exception {
        return this.orderMapper.getOrderDetail(param);
    }

    @Override
    @Transactional
    public int createOrder(CreateOrderParam param) throws Exception {
        int resultNum = this.orderMapper.createOrder(param);

        if (resultNum != 0 && param.getProducts() != null) {
            for (CreateOrderProductParam product : param.getProducts()) {
                product.setOrderId(param.getOrderId());
                this.orderMapper.createOrderDetail(product);
            }
        }

        return resultNum;
    }
}