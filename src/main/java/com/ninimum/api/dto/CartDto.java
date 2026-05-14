package com.ninimum.api.dto;

import lombok.Data;

@Data
public class CartDto {
    private Long cartId;
    private Long userId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer price;
    private Integer quantity;
    private Integer totalPrice;
}