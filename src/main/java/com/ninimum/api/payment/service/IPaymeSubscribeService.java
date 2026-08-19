package com.ninimum.api.payment.service;

import com.ninimum.api.dto.payme.subscribe.PaymeCardTokenResult;
import com.ninimum.api.dto.payme.subscribe.PaymeCreateCardParam;
import com.ninimum.api.dto.payme.subscribe.PaymeVerifyCardParam;

public interface IPaymeSubscribeService {

    PaymeCardTokenResult createCard(PaymeCreateCardParam param) throws Exception;

    Boolean getVerifyCode(String token) throws Exception;

    PaymeCardTokenResult verifyCard(PaymeVerifyCardParam param) throws Exception;
}