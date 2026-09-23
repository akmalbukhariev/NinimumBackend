package com.ninimum.api.param;

import lombok.Data;

import java.util.List;

@Data
public class UpdateReviewParam {
    private Long id;
    private Long user_id;
    private Integer rating;
    private String comment;
    private List<Long> keep_image_ids;
    private List<ReviewImageParam> images;
}
