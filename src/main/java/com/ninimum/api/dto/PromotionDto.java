package com.ninimum.api.dto;

import lombok.Data;

@Data
public class PromotionDto {
    private Long promotionId;
    private String title;
    private String content;
    private String imageUrl;
    private String startDate;
    private String endDate;
}