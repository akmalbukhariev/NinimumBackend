package com.ninimum.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDetailDto {
    private Long orderItemId;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal totalPrice;
}