package com.ninimum.api.response;

import lombok.Data;

@Data
public class ReviewEligibilityResponse {
    private Boolean can_review;
    private Boolean has_purchased;
    private Boolean already_reviewed;
    private Long order_id;
    private ReviewResponse existing_review;
}
