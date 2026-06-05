package com.ninimum.api.dto;

import lombok.Data;

@Data
public class ProductImageDto {
    private Long id;
    private Long product_id;
    private String image_url;
    private Integer sort_order;
}
