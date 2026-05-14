package com.ninimum.api.payment.service.impl;

import com.ninimum.api.dto.PaymentDto;
import com.ninimum.api.param.CreatePaymentParam;
import com.ninimum.api.param.PaymentDetailParam;
import com.ninimum.api.param.PaymentListParam;
import com.ninimum.api.payment.service.IPaymentService;
import com.ninimum.api.payment.service.PaymentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService implements IPaymentService {

    private final PaymentMapper paymentMapper;

    @Override
    public List<PaymentDto> getPaymentList(PaymentListParam param) throws Exception {
        return this.paymentMapper.getPaymentList(param);
    }

    @Override
    public PaymentDto getPaymentDetail(PaymentDetailParam param) throws Exception {
        return this.paymentMapper.getPaymentDetail(param);
    }

    @Override
    public int createPayment(CreatePaymentParam param) throws Exception {
        return this.paymentMapper.createPayment(param);
    }
}