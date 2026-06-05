package com.ninimum.api.param;

import lombok.Data;

@Data
public class SearchProductParam extends PageSizeParam {
    private String keyword;
    private Double minPrice;
    private Double maxPrice;
    private String sortType; // cheap, expensive, newest, oldest
}