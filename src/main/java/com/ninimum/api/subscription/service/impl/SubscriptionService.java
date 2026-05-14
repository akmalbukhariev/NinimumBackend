package com.ninimum.api.subscription.service.impl;

import com.ninimum.api.dto.SubscriptionDto;
import com.ninimum.api.param.ActiveSubscriptionParam;
import com.ninimum.api.param.CreateSubscriptionParam;
import com.ninimum.api.param.SubscriptionListParam;
import com.ninimum.api.subscription.service.ISubscriptionService;
import com.ninimum.api.subscription.service.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService implements ISubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    @Override
    public List<SubscriptionDto> getSubscriptionList(SubscriptionListParam param) throws Exception {
        return this.subscriptionMapper.getSubscriptionList(param);
    }

    @Override
    public SubscriptionDto getActiveSubscription(ActiveSubscriptionParam param) throws Exception {
        return this.subscriptionMapper.getActiveSubscription(param);
    }

    @Override
    public int createSubscription(CreateSubscriptionParam param) throws Exception {
        return this.subscriptionMapper.createSubscription(param);
    }
}