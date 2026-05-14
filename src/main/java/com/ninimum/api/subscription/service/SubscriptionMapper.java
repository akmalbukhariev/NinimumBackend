package com.ninimum.api.subscription.service;

import com.ninimum.api.dto.SubscriptionDto;
import com.ninimum.api.param.ActiveSubscriptionParam;
import com.ninimum.api.param.CreateSubscriptionParam;
import com.ninimum.api.param.SubscriptionListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SubscriptionMapper {
    List<SubscriptionDto> getSubscriptionList(SubscriptionListParam param) throws Exception;

    SubscriptionDto getActiveSubscription(ActiveSubscriptionParam param) throws Exception;

    int createSubscription(CreateSubscriptionParam param) throws Exception;
}