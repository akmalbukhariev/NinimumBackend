package com.ninimum.api.payment.service;

import com.ninimum.api.dto.payme.CreatePaymeCheckoutUrlParam;
import com.ninimum.api.dto.payme.PaymeRequest;
import com.ninimum.api.dto.payme.PaymeResponse;
import com.ninimum.api.response.payme.CreatePaymeCheckoutUrlResponse;

public interface IPaymeService {
    boolean isValidAuthorization(String authorization);
    PaymeResponse handleRequest(PaymeRequest request);
    CreatePaymeCheckoutUrlResponse createCheckoutUrl(CreatePaymeCheckoutUrlParam param) throws Exception;
}