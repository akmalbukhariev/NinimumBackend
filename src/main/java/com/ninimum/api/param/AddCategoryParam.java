package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddCategoryParam {
    private Long parent_id;
    private String name;
    private String image_url;
    private Integer sort_order;
}