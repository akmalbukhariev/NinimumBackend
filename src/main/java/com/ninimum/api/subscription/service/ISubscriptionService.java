package com.ninimum.api.subscription.service;

import com.ninimum.api.dto.SubscriptionDto;
import com.ninimum.api.dto.TariffPaymentStatusDto;
import com.ninimum.api.param.ActiveSubscriptionParam;
import com.ninimum.api.param.CreateSubscriptionParam;
import com.ninimum.api.param.CreateTariffCheckoutParam;
import com.ninimum.api.param.SubscriptionListParam;
import com.ninimum.api.param.TariffPaymentStatusParam;
import com.ninimum.api.response.payme.CreateTariffCheckoutUrlResponse;

import java.util.List;

public interface ISubscriptionService {
    List<SubscriptionDto> getSubscriptionList(SubscriptionListParam param) throws Exception;

    SubscriptionDto getActiveSubscription(ActiveSubscriptionParam param) throws Exception;

    int createSubscription(CreateSubscriptionParam param) throws Exception;

    CreateTariffCheckoutUrlResponse createCheckoutUrl(CreateTariffCheckoutParam param) throws Exception;

    TariffPaymentStatusDto getPaymentStatus(TariffPaymentStatusParam param) throws Exception;
}
