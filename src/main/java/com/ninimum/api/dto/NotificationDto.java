package com.ninimum.api.dto;

import lombok.Data;

@Data
public class NotificationDto {
    private Long notificationId;
    private Long userId;
    private String title;
    private String content;
    private String readYn;
    private String createdDate;
}