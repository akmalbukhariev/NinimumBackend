package com.ninimum.api.payment.service;

import com.ninimum.api.camelcase.CamelCaseMap;
import com.ninimum.api.dto.payme.*;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaymeMapper {
    CamelCaseMap getPaymeOrderCheckById(GetPaymeOrderParam param);

    List<CamelCaseMap> getPaymeOrderFiscalItems(GetPaymeOrderParam param);

    CamelCaseMap getPaymePaymentByTransactionId(GetPaymePaymentParam param);

    CamelCaseMap getActivePaymePaymentByOrderId(GetPaymeOrderParam param);

    int createPaymePayment(CreatePaymePaymentParam param);

    int performPaymePayment(PerformPaymePaymentParam param);

    int updateOrderPaymentStatus(UpdateOrderPaymentStatusParam param);
    int cancelPaymePayment(CancelPaymePaymentParam param);

    List<CamelCaseMap> getPaymeStatement(GetPaymeStatementParam param);
}
