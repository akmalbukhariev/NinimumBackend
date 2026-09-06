package com.ninimum.api.param;

import lombok.Data;

@Data
public class ProductQuestionListParam extends PageSizeParam {
    private Long product_id;
}
