package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteReviewParam {
    private Long reviewId;
    private Long userId;
}