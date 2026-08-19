package com.ninimum.api.dto.payme.subscribe;

import lombok.Data;

@Data
public class PaymeCreateCardParam {
    private String number;
    private String expire;
}