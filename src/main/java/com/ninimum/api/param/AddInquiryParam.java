package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddInquiryParam {
    private Long userId;
    private String title;
    private String content;
}