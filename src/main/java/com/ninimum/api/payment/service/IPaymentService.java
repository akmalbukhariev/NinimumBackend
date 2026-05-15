package com.ninimum.api.payment.service;

import com.ninimum.api.dto.PaymentDto;
import com.ninimum.api.param.CancelPaymentParam;
import com.ninimum.api.param.CreatePaymentParam;
import com.ninimum.api.param.PaymentDetailParam;
import com.ninimum.api.param.PaymentListParam;

import java.util.List;

public interface IPaymentService {
    List<PaymentDto> getPaymentList(PaymentListParam param) throws Exception;

    PaymentDto getPaymentDetail(PaymentDetailParam param) throws Exception;

    int createPayment(CreatePaymentParam param) throws Exception;
    int cancelPayment(CancelPaymentParam param) throws Exception;
}