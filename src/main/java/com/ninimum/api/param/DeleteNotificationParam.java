package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteNotificationParam {
    private Long notificationId;
    private Long userId;
}