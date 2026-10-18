package com.ninimum.api.param;

import lombok.Data;

@Data
public class UpdateCartParam {
    @com.fasterxml.jackson.annotation.JsonAlias("cart_id")
    private Long cartId;
    @com.fasterxml.jackson.annotation.JsonAlias("user_id")
    private Long userId;
    private Integer quantity;
}