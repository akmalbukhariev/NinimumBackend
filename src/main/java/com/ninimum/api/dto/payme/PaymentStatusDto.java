package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class PaymentStatusDto {
    private Long orderId;
    private String orderNumber;
    private String paymentStatus;
}
