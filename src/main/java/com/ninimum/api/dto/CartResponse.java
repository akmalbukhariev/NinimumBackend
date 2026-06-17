package com.ninimum.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartResponse extends ProductDto {
    private Long cart_id;
    private Integer quantity;
    private Boolean liked;
    private List<ProductImageDto> images;
}