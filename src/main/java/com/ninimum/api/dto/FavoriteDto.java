package com.ninimum.api.dto;

import lombok.Data;

@Data
public class FavoriteDto {
    private Long favoriteId;
    private Long userId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer price;
    private String description;
}