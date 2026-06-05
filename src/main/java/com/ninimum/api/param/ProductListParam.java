package com.ninimum.api.param;

import lombok.Data;

@Data
public class ProductListParam extends PageSizeParam {
    private Long user_id;
    private Long category_id;
}