package com.ninimum.api.dto;

import lombok.Data;

@Data
public class ProductCheckoutPriceDto {
    private Long productId;
    private Integer price;
    private Integer subscriptionPrice;
}
