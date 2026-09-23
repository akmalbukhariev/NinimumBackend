package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreateTariffCheckoutParam {
    private Long userId;
    private Long tariffId;
}
