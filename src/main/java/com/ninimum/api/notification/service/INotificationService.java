package com.ninimum.api.notification.service;

import com.ninimum.api.dto.NotificationDto;
import com.ninimum.api.param.DeleteNotificationParam;
import com.ninimum.api.param.NotificationListParam;
import com.ninimum.api.param.ReadNotificationParam;

import java.util.List;

public interface INotificationService {
    List<NotificationDto> getNotificationList(NotificationListParam param) throws Exception;

    int readNotification(ReadNotificationParam param) throws Exception;

    int deleteNotification(DeleteNotificationParam param) throws Exception;
}