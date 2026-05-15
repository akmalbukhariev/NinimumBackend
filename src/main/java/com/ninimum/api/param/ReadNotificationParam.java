package com.ninimum.api.param;

import lombok.Data;

@Data
public class ReadNotificationParam {
    private Long notificationId;
    private Long userId;
}