package com.ninimum.api.param;

import lombok.Data;

@Data
public class ForgotPasswordParam {
    private String phoneNumber;
    private String tempPassword;
}