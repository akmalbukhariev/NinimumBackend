package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class PaymentStatusDto {
    private Long orderId;
    private String orderNumber;
    private String paymentStatus;
    /** Payme's cancel reason of the latest Payme payment, if it failed (see PaymeService.cancelTransaction). */
    private Integer paymentReason;
}
