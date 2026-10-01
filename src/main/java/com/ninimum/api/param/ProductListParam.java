package com.ninimum.api.param;

import lombok.Data;

@Data
public class ProductListParam extends PageSizeParam {
    private Long user_id;
    private Long category_id;
    private boolean include_subcategories;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private java.util.List<Long> categoryIds;
}