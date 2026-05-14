package com.ninimum.api.dto;

import lombok.Data;

@Data
public class ReviewDto {
    private Long reviewId;
    private Long userId;
    private Long productId;
    private String userName;
    private Integer rating;
    private String content;
    private String createdDate;
}