package com.ninimum.api.dto.payme.subscribe;

import lombok.Data;

@Data
public class PaymeVerifyCardParam {
    private String token;
    private String code;
}