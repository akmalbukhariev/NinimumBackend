package com.ninimum.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductDto {
    private Long id;
    private Long category_id;
    private String name;
    private String short_description;
    private String description;
    private String sku;
    private String barcode;
    private String brand;
    private Double price;
    private Double subscription_price;
    private Integer stock_quantity;
    private Double weight_gram;
    private Boolean is_active;
    private Boolean is_featured;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime created_at;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updated_at;
}