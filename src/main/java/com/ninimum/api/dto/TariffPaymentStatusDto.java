package com.ninimum.api.dto;

import lombok.Data;

@Data
public class TariffPaymentStatusDto {
    private Long subscriptionId;
    private String paymentStatus;
    private String subscriptionStatus;
}
