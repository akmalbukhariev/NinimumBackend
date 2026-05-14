package com.ninimum.api.dto;

import lombok.Data;

@Data
public class OrderDetailDto {
    private Long orderDetailId;
    private Long orderId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer price;
    private Integer quantity;
    private Integer totalPrice;
}