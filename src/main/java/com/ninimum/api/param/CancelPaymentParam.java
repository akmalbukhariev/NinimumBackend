package com.ninimum.api.param;

import lombok.Data;

@Data
public class CancelPaymentParam {
    private Long paymentId;
    private Long userId;
}