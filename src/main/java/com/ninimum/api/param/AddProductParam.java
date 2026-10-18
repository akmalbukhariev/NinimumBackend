package com.ninimum.api.param;

import lombok.Data;

import java.util.List;

@Data
public class AddProductParam {

    private Long id;

    private Long category_id;

    private Long fiscal_mxik_package_id;
    private Integer vat_percent;

    private String name;
    private String short_description;
    private String description;
    private String sku;
    private String barcode;
    private String brand;

    private java.math.BigDecimal cost_price;
    private Integer min_stock_quantity;
    private Integer shelf_life_days;
    private String storage_temperature;
    private String ingredients;
    private String age_range;
    private Double price;
    private Double subscription_price;

    private Integer stock_quantity;
    private Double weight_gram;
    private Boolean is_featured;

    private List<ProductImageParam> images;
}