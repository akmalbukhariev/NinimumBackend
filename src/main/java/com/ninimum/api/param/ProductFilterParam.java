package com.ninimum.api.param;

import lombok.Data;

@Data
public class ProductFilterParam {
    private Long categoryId;
    private String keyword;
    private Integer minPrice;
    private Integer maxPrice;
}