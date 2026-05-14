package com.ninimum.api.dto;

import lombok.Data;

@Data
public class FaqDto {
    private Long faqId;
    private String question;
    private String answer;
}