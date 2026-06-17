package com.ninimum.api.param;

import lombok.Data;

import java.util.List;

@Data
public class AddReviewParam {
    private Long id;

    private Long user_id;
    private Long product_id;
    private Long order_id;

    private Integer rating;
    private String comment;

    private List<ReviewImageParam> images;
}