package com.ninimum.api.param;

import lombok.Data;

@Data
public class TariffPaymentStatusParam {
    private Long userId;
    private Long subscriptionId;
}
