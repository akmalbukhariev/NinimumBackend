package com.ninimum.api.dto;

import lombok.Data;

@Data
public class BannerDto {

    private Long id;
    private Long product_id;
    private Integer sort_order;
    private Integer isActive;

    // from products table
    private String name;
    private String shortDescription;
    private String description;
    private String brand;
    private Double price;
    private Double subscriptionPrice;

    // from product_images table
    private String imageUrl;
}