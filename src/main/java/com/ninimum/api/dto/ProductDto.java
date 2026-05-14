package com.ninimum.api.dto;

import lombok.Data;

@Data
public class ProductDto {
    private Long productId;
    private Long categoryId;
    private String productName;
    private String productImageUrl;
    private Integer price;
    private String description;
}