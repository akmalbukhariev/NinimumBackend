package com.ninimum.api.payment.service;

import com.ninimum.api.dto.PaymentDto;
import com.ninimum.api.param.CreatePaymentParam;
import com.ninimum.api.param.PaymentDetailParam;
import com.ninimum.api.param.PaymentListParam;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaymentMapper {
    List<PaymentDto> getPaymentList(PaymentListParam param) throws Exception;

    PaymentDto getPaymentDetail(PaymentDetailParam param) throws Exception;

    int createPayment(CreatePaymentParam param) throws Exception;
}