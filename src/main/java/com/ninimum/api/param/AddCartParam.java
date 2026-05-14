package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddCartParam {
    private Long userId;
    private Long productId;
    private Integer quantity;
}