package com.ninimum.api.payment.service.impl;

import com.ninimum.api.dto.PaymentCardDto;
import com.ninimum.api.param.*;
import com.ninimum.api.payment.service.IPaymentService;
import com.ninimum.api.payment.service.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService implements IPaymentService {

    private final PaymentMapper paymentMapper;

    @Override
    public List<PaymentCardDto> getPaymentCardList(PaymentCardListParam param) throws Exception {
        return paymentMapper.getPaymentCardList(param);
    }

    @Override
    @Transactional
    public int createPaymentCard(CreatePaymentCardParam param) throws Exception {

        String cardNumber = param.getCard_number().replace(" ", "");

        String cardHash = DigestUtils.sha256Hex(cardNumber);
        String cardBrand = detectCardBrand(cardNumber);
        String lastFourDigits = cardNumber.substring(cardNumber.length() - 4);
        String paymentToken = "mock_" + UUID.randomUUID();

        param.setCard_hash(cardHash);
        param.setCard_brand(cardBrand);
        param.setLast_four_digits(lastFourDigits);
        param.setPayment_token(paymentToken);

        if (Boolean.TRUE.equals(param.getIs_default())) {
            paymentMapper.clearDefaultPaymentCard(param);
        }

        return paymentMapper.createPaymentCard(param);
    }

    @Override
    public int deletePaymentCard(DeletePaymentCardParam param) throws Exception {
        return paymentMapper.deletePaymentCard(param);
    }

    @Override
    @Transactional
    public int setDefaultPaymentCard(SetDefaultPaymentCardParam param) throws Exception {
        paymentMapper.clearDefaultPaymentCardByUser(param);
        return paymentMapper.setDefaultPaymentCard(param);
    }

    @Override
    public int hasPaymentCard(PaymentCardListParam param) throws Exception {
        return paymentMapper.hasPaymentCard(param);
    }

    private String detectCardBrand(String cardNumber) {
        if (cardNumber.startsWith("4")) {
            return "VISA";
        }

        if (cardNumber.startsWith("5")) {
            return "MASTERCARD";
        }

        if (cardNumber.startsWith("8600")) {
            return "UZCARD";
        }

        if (cardNumber.startsWith("9860")) {
            return "HUMO";
        }

        return "UNKNOWN";
    }
}