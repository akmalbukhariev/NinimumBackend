package com.ninimum.api.dto;

import lombok.Data;

@Data
public class RecentlyViewedProductDto {
    private Long viewId;
    private Long userId;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer price;
    private String viewedDate;
}