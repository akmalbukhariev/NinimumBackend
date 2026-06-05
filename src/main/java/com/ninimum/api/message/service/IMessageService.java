package com.ninimum.api.message.service;

import com.ninimum.api.param.VerifyPhoneNumberParam;
import com.ninimum.api.response.VerifyPhoneNumberResponse;

public interface IMessageService {
    VerifyPhoneNumberResponse verifyPhoneNumber(VerifyPhoneNumberParam param) throws Exception;

    String sendTemporaryPassword(String phoneNumber) throws Exception;
}
