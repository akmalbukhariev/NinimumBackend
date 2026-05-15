package com.ninimum.api.dto;

import lombok.Data;

@Data
public class InquiryDto {
    private Long inquiryId;
    private Long userId;
    private String title;
    private String content;
    private String answer;
    private String inquiryStatus;
    private String createdDate;
}