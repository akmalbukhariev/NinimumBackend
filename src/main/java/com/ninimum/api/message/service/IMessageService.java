package com.ninimum.api.message.service;

import com.ninimum.api.param.VerifyPhoneNumberParam;
import com.ninimum.api.response.VerifyPhoneNumberResponse;

public interface IMessageService {
    /** Sends a code SMS (login text for an existing account) and returns the code for SmsCodes, or null if not sent. */
    String deliverCode(String phone, boolean login) throws Exception;

    String sendTemporaryPassword(String phoneNumber) throws Exception;
}
