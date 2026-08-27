package com.ninimum.api.subscription.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.SubscriptionDto;
import com.ninimum.api.dto.TariffPaymentStatusDto;
import com.ninimum.api.param.ActiveSubscriptionParam;
import com.ninimum.api.param.CreateSubscriptionParam;
import com.ninimum.api.param.SubscriptionListParam;
import com.ninimum.api.param.TariffPaymentStatusParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubscriptionMapper {
    List<SubscriptionDto> getSubscriptionList(SubscriptionListParam param) throws Exception;

    SubscriptionDto getActiveSubscription(ActiveSubscriptionParam param) throws Exception;

    int createSubscription(CreateSubscriptionParam param) throws Exception;

    int expireSubscriptions(@Param("userId") Long userId) throws Exception;

    CamelCaseMap getTariffForCheckout(@Param("tariffId") Long tariffId) throws Exception;

    int cancelPendingSubscriptions(@Param("userId") Long userId) throws Exception;

    int createPendingSubscription(CreateSubscriptionParam param) throws Exception;

    TariffPaymentStatusDto getPaymentStatus(TariffPaymentStatusParam param) throws Exception;
}
