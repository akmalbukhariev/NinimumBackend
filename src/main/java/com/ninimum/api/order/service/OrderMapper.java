package com.ninimum.api.order.service;

import com.ninimum.api.dto.OrderDetailDto;
import com.ninimum.api.dto.OrderDto;
import com.ninimum.api.dto.OrderProcessDto;
import com.ninimum.api.dto.payme.PaymentStatusDto;
import com.ninimum.api.param.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderMapper {
    List<OrderDto> getOrderList(OrderListParam param) throws Exception;
    List<OrderDetailDto> getOrderDetail(OrderDetailParam param) throws Exception;
    int createOrder(CreateOrderParam param) throws Exception;
    int createOrderItem(CreateOrderProductParam param) throws Exception;
    int cancelOrder(CancelOrderParam param) throws Exception;
    int getOrderCount(OrderListParam param) throws Exception;
    PaymentStatusDto getOrderPaymentStatus(OrderDetailParam param) throws Exception;
    OrderProcessDto getOrderProcess(OrderDetailParam param) throws Exception;
}