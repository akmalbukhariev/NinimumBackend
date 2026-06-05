package com.ninimum.api.response;

import lombok.Data;

@Data
public class VerifyPhoneNumberResponse {
    private String phone_number;
    private String code;
}
