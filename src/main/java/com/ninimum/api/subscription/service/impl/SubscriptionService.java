package com.ninimum.api.subscription.service.impl;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.SubscriptionDto;
import com.ninimum.api.dto.TariffPaymentStatusDto;
import com.ninimum.api.param.ActiveSubscriptionParam;
import com.ninimum.api.param.CreateSubscriptionParam;
import com.ninimum.api.param.CreateTariffCheckoutParam;
import com.ninimum.api.param.SubscriptionListParam;
import com.ninimum.api.param.TariffPaymentStatusParam;
import com.ninimum.api.response.payme.CreateTariffCheckoutUrlResponse;
import com.ninimum.api.subscription.service.ISubscriptionService;
import com.ninimum.api.subscription.service.SubscriptionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SubscriptionService implements ISubscriptionService {

    private final SubscriptionMapper subscriptionMapper;

    @Value("${payme.merchant-id}")
    private String paymeMerchantId;

    @Value("${payme.checkout-url}")
    private String paymeCheckoutUrl;

    @Value("${payme.return-url}")
    private String paymeReturnUrl;

    @Value("${payme.tariff-account-offset:1000000000000}")
    private long tariffAccountOffset;

    @Override
    public List<SubscriptionDto> getSubscriptionList(SubscriptionListParam param) throws Exception {
        validateUserId(param == null ? null : param.getUserId());
        subscriptionMapper.expireSubscriptions(param.getUserId());
        return subscriptionMapper.getSubscriptionList(param);
    }

    @Override
    public SubscriptionDto getActiveSubscription(ActiveSubscriptionParam param) throws Exception {
        validateUserId(param == null ? null : param.getUserId());
        subscriptionMapper.expireSubscriptions(param.getUserId());
        return subscriptionMapper.getActiveSubscription(param);
    }

    @Override
    public int createSubscription(CreateSubscriptionParam param) throws Exception {
        throw new Exception("Direct tariff activation is disabled. Use Payme checkout.");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateTariffCheckoutUrlResponse createCheckoutUrl(CreateTariffCheckoutParam param) throws Exception {
        validateUserId(param == null ? null : param.getUserId());

        if (param.getTariffId() == null || param.getTariffId() <= 0) {
            throw new Exception("tariffId is required");
        }

        subscriptionMapper.expireSubscriptions(param.getUserId());

        ActiveSubscriptionParam activeParam = new ActiveSubscriptionParam();
        activeParam.setUserId(param.getUserId());

        SubscriptionDto activeSubscription = subscriptionMapper.getActiveSubscription(activeParam);
        if (activeSubscription != null &&
                activeSubscription.getSubscriptionId() != null &&
                activeSubscription.getTariffId() != null &&
                activeSubscription.getTariffId().equals(param.getTariffId())) {
            throw new Exception("Selected tariff is already active");
        }

        // An ACTIVE tariff does not block checkout for a different tariff.
        // The current tariff remains ACTIVE while the user is paying. It is
        // expired only after Payme confirms the new tariff payment.
        CamelCaseMap tariff = subscriptionMapper.getTariffForCheckout(param.getTariffId());
        if (tariff == null) {
            throw new Exception("Tariff not found or inactive");
        }

        BigDecimal price = new BigDecimal(String.valueOf(tariff.get("price")));
        if (price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new Exception("Tariff price is invalid");
        }

        // A new checkout replaces an unfinished PENDING tariff attempt.
        // If Payme later tries to perform an old cancelled attempt, PaymeService rejects it.
        subscriptionMapper.cancelPendingSubscriptions(param.getUserId());

        CreateSubscriptionParam createParam = new CreateSubscriptionParam();
        createParam.setUserId(param.getUserId());
        createParam.setTariffId(param.getTariffId());

        int created = subscriptionMapper.createPendingSubscription(createParam);
        if (created != 1 || createParam.getSubscriptionId() == null) {
            throw new Exception("Tariff checkout could not be created");
        }

        long amountTiyin = price.movePointRight(2).longValueExact();

        // Payme merchant account is configured with the single field `order_id`.
        // Tariffs therefore use the same account field with a reserved numeric offset.
        // Example: subscriptionId=7 -> ac.order_id=1000000000007.
        long paymeAccountId = Math.addExact(tariffAccountOffset, createParam.getSubscriptionId());

        String paramsText =
                "m=" + paymeMerchantId +
                        ";ac.order_id=" + paymeAccountId +
                        ";a=" + amountTiyin +
                        ";l=uz" +
                        ";c=" + paymeReturnUrl;

        String encoded = Base64.getEncoder()
                .encodeToString(paramsText.getBytes(StandardCharsets.UTF_8));

        String paymentUrl = paymeCheckoutUrl + encoded;

        return new CreateTariffCheckoutUrlResponse(createParam.getSubscriptionId(), paymentUrl);
    }

    @Override
    public TariffPaymentStatusDto getPaymentStatus(TariffPaymentStatusParam param) throws Exception {
        validateUserId(param == null ? null : param.getUserId());

        if (param.getSubscriptionId() == null || param.getSubscriptionId() <= 0) {
            throw new Exception("subscriptionId is required");
        }

        TariffPaymentStatusDto result = subscriptionMapper.getPaymentStatus(param);
        if (result == null) {
            throw new Exception("Tariff subscription not found");
        }

        if (result.getPaymentStatus() == null || result.getPaymentStatus().trim().isEmpty()) {
            result.setPaymentStatus("PENDING");
        }

        return result;
    }

    private void validateUserId(Long userId) throws Exception {
        if (userId == null || userId <= 0) {
            throw new Exception("userId is required");
        }
    }
}
