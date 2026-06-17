package com.ninimum.api.payment.service;

import com.ninimum.api.dto.PaymentCardDto;
import com.ninimum.api.param.*;

import java.util.List;

public interface IPaymentService {
    List<PaymentCardDto> getPaymentCardList(PaymentCardListParam param) throws Exception;

    int createPaymentCard(CreatePaymentCardParam param) throws Exception;

    int deletePaymentCard(DeletePaymentCardParam param) throws Exception;

    int setDefaultPaymentCard(SetDefaultPaymentCardParam param) throws Exception;

    int hasPaymentCard(PaymentCardListParam param) throws Exception;
}