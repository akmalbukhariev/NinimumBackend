package com.ninimum.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDto {
    private Long orderId;
    private Long userId;
    private String orderNumber;
    private String status;
    private String paymentStatus;
    private BigDecimal totalPrice;
    private Integer productCount;
    private String orderedAt;
}