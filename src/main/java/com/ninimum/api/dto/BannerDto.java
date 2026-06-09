package com.ninimum.api.dto;

import lombok.Data;

@Data
public class BannerDto {

    private Long id;
    private Long product_id;
    private Integer sort_order;
    private Boolean is_active;

    private String name;
    private String short_description;
    private String description;
    private String brand;
    private Double price;
    private Double subscription_price;

    private String image_url;
}