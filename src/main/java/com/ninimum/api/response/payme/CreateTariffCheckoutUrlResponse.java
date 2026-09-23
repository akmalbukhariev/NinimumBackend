package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateTariffCheckoutUrlResponse {
    private Long subscriptionId;
    private String paymentUrl;
}
