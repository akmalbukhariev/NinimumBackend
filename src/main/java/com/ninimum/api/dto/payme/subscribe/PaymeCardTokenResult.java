package com.ninimum.api.dto.payme.subscribe;

import lombok.Data;

@Data
public class PaymeCardTokenResult {
    private String number;
    private String expire;
    private String token;
    private Boolean recurrent;
    private Boolean verify;
}