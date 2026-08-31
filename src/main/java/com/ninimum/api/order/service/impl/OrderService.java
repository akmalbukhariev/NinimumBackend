package com.ninimum.api.order.service.impl;

import com.ninimum.api.dto.OrderCountDto;
import com.ninimum.api.dto.OrderDetailDto;
import com.ninimum.api.dto.OrderDto;
import com.ninimum.api.dto.OrderProcessDto;
import com.ninimum.api.dto.ProductCheckoutPriceDto;
import com.ninimum.api.dto.payme.PaymentStatusDto;
import com.ninimum.api.order.service.IOrderService;
import com.ninimum.api.order.service.OrderMapper;
import com.ninimum.api.param.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderMapper orderMapper;

    @Value("${file.access.url}")
    private String fileAccessUrl;

    @Override
    public List<OrderDto> getOrderList(OrderListParam param) throws Exception {
        return orderMapper.getOrderList(param);
    }

    @Override
    public List<OrderDetailDto> getOrderDetail(OrderDetailParam param) throws Exception {

        List<OrderDetailDto> orderDetails = orderMapper.getOrderDetail(param);

        for (OrderDetailDto detail : orderDetails) {
            if (detail.getProductImageUrl() != null && !detail.getProductImageUrl().isEmpty()) {
                detail.setProductImageUrl(fileAccessUrl + "/" + detail.getProductImageUrl());
            }
        }

        return orderDetails;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int createOrder(CreateOrderParam param) throws Exception {

        if (param.getUserId() == null) {
            throw new Exception("User ID is required");
        }

        if (param.getAddressId() == null) {
            throw new Exception("Address ID is required");
        }

        if (param.getProducts() == null || param.getProducts().isEmpty()) {
            throw new Exception("Order must contain at least one product");
        }

        boolean useTariffPrice = false;

        if (param.getTariffSubscriptionId() != null && param.getTariffSubscriptionId() > 0) {
            useTariffPrice = orderMapper.isActiveTariffSubscription(param) > 0;

            if (!useTariffPrice) {
                throw new Exception("Tariff subscription is not active for this user");
            }
        }

        long regularSubtotal = 0;
        long calculatedTotal = 0;

        for (CreateOrderProductParam product : param.getProducts()) {
            if (product.getProductId() == null) {
                throw new Exception("Product ID is required");
            }

            if (product.getQuantity() == null || product.getQuantity() <= 0) {
                throw new Exception("Quantity must be greater than zero. product_id=" + product.getProductId());
            }

            ProductCheckoutPriceDto productPrice = orderMapper.getProductCheckoutPrice(product.getProductId());

            if (productPrice == null || productPrice.getPrice() == null || productPrice.getPrice() <= 0) {
                throw new Exception("Product not found or price is invalid. product_id=" + product.getProductId());
            }

            int regularUnitPrice = productPrice.getPrice();
            int unitPrice = regularUnitPrice;

            if (useTariffPrice && productPrice.getSubscriptionPrice() != null && productPrice.getSubscriptionPrice() > 0) {
                unitPrice = productPrice.getSubscriptionPrice();
            }

            product.setPrice(unitPrice);
            regularSubtotal += (long) regularUnitPrice * product.getQuantity();
            calculatedTotal += (long) unitPrice * product.getQuantity();
        }

        if (calculatedTotal <= 0 || calculatedTotal > Integer.MAX_VALUE || regularSubtotal > Integer.MAX_VALUE) {
            throw new Exception("Calculated order total is invalid");
        }

        long discount = Math.max(0, regularSubtotal - calculatedTotal);

        // Never trust the mobile total/price. The server recalculates everything from products + active tariff.
        param.setSubtotalPrice((int) regularSubtotal);
        param.setDiscountPrice((int) discount);
        param.setTotalPrice((int) calculatedTotal);

        int resultNum = orderMapper.createOrder(param);

        if (resultNum != 1 || param.getOrderId() == null) {
            throw new Exception("Order could not be created");
        }

        for (CreateOrderProductParam product : param.getProducts()) {
            product.setOrderId(param.getOrderId());

            int itemResult = orderMapper.createOrderItem(product);

            if (itemResult != 1) {
                throw new Exception(
                        "Order item could not be created. " +
                                "Product may be missing MXIK, package code, or VAT. " +
                                "product_id=" + product.getProductId()
                );
            }
        }

        return resultNum;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cancelOrder(CancelOrderParam param) throws Exception {
        if (param == null || param.getOrderId() == null || param.getUserId() == null) {
            throw new Exception("Order ID and User ID are required");
        }

        if (param.getReason() == null || param.getReason().trim().isEmpty()) {
            throw new Exception("Cancel reason is required");
        }

        param.setReason(param.getReason().trim());

        int result = orderMapper.cancelOrder(param);

        if (result != 1) {
            throw new Exception("Order cannot be cancelled. It may already be processing, delivered, cancelled, or not belong to this user");
        }

        return result;
    }

    @Override
    public OrderCountDto getOrderCount(OrderListParam param) throws Exception {

        int count = orderMapper.getOrderCount(param);

        OrderCountDto dto = new OrderCountDto();
        dto.setOrderCount(count);

        return dto;
    }

    @Override
    public PaymentStatusDto getOrderPaymentStatus(OrderDetailParam param) throws Exception {

        if (param == null || param.getOrderId() == null || param.getUserId() == null) {
            throw new Exception("Order ID and User ID are required");
        }

        PaymentStatusDto result = orderMapper.getOrderPaymentStatus(param);

        if (result == null) {
            throw new Exception("Order not found");
        }

        return result;
    }

    @Override
    public OrderProcessDto getOrderProcess(OrderDetailParam param) throws Exception {

        if (param == null || param.getOrderId() == null || param.getUserId() == null) {
            throw new Exception("Order ID and User ID are required");
        }

        OrderProcessDto result = orderMapper.getOrderProcess(param);

        if (result == null) {
            throw new Exception("Order not found");
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteOrderHistory(OrderDetailParam param) throws Exception {
        if (param == null || param.getOrderId() == null || param.getUserId() == null) {
            throw new Exception("Order ID and User ID are required");
        }

        int result = orderMapper.deleteOrderHistory(param);

        if (result != 1) {
            throw new Exception(
                    "Order history cannot be deleted. " +
                            "The order may not belong to this user, may already be deleted, " +
                            "or may not be completed yet"
            );
        }

        return result;
    }
}