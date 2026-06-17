package com.ninimum.api.param;

import lombok.Data;

@Data
public class ReviewListParam extends PageSizeParam {
    private Long product_id;
}