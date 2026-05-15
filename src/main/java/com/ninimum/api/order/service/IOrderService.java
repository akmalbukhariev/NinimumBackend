package com.ninimum.api.order.service;

import com.ninimum.api.dto.OrderCountDto;
import com.ninimum.api.dto.OrderDetailDto;
import com.ninimum.api.dto.OrderDto;
import com.ninimum.api.param.CancelOrderParam;
import com.ninimum.api.param.CreateOrderParam;
import com.ninimum.api.param.OrderDetailParam;
import com.ninimum.api.param.OrderListParam;

import java.util.List;

public interface IOrderService {
    List<OrderDto> getOrderList(OrderListParam param) throws Exception;

    List<OrderDetailDto> getOrderDetail(OrderDetailParam param) throws Exception;

    int createOrder(CreateOrderParam param) throws Exception;
    int cancelOrder(CancelOrderParam param) throws Exception;
    OrderCountDto getOrderCount(OrderListParam param) throws Exception;
}