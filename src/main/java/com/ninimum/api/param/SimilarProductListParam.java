package com.ninimum.api.param;

import lombok.Data;

@Data
public class SimilarProductListParam extends PageSizeParam{
    private Long user_id;
    private Long product_id;
}
