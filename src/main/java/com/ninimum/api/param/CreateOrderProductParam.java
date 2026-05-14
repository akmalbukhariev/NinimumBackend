package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreateOrderProductParam {
    private Long orderId;
    private Long productId;
    private Integer price;
    private Integer quantity;
}