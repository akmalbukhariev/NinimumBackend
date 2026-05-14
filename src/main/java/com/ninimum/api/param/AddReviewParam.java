package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddReviewParam {
    private Long userId;
    private Long productId;
    private Integer rating;
    private String content;
}