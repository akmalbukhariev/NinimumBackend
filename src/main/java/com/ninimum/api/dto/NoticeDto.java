package com.ninimum.api.dto;

import lombok.Data;

@Data
public class NoticeDto {
    private Long noticeId;
    private String title;
    private String content;
    private String createdDate;
}