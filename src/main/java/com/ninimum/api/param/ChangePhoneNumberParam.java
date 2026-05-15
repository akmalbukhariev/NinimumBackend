package com.ninimum.api.param;

import lombok.Data;

@Data
public class ChangePhoneNumberParam {
    private Long userId;
    private String phoneNumber;
}