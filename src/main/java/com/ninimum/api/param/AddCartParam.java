package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddCartParam {
    private Long user_id;
    private Long product_id;
    private Integer quantity;
}