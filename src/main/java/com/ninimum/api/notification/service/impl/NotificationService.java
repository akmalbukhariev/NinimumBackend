package com.ninimum.api.notification.service.impl;

import com.ninimum.api.dto.NotificationDto;
import com.ninimum.api.notification.service.INotificationService;
import com.ninimum.api.notification.service.NotificationMapper;
import com.ninimum.api.param.DeleteNotificationParam;
import com.ninimum.api.param.NotificationListParam;
import com.ninimum.api.param.ReadNotificationParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService implements INotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public List<NotificationDto> getNotificationList(NotificationListParam param) throws Exception {
        return this.notificationMapper.getNotificationList(param);
    }

    @Override
    public int readNotification(ReadNotificationParam param) throws Exception {
        return this.notificationMapper.readNotification(param);
    }

    @Override
    public int deleteNotification(DeleteNotificationParam param) throws Exception {
        return this.notificationMapper.deleteNotification(param);
    }
}