package com.ninimum.api.param;

import lombok.Data;

@Data
public class UpdateCartParam {
    private Long cartId;
    private Long userId;
    private Integer quantity;
}