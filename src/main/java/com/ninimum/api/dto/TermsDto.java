package com.ninimum.api.dto;

import lombok.Data;

@Data
public class TermsDto {
    private Long termsId;
    private String termsType;
    private String title;
    private String content;
    private String requiredYn;
    private String createdDate;
}