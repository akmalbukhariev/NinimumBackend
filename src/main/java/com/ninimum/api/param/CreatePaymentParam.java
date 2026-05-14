package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreatePaymentParam {
    private Long orderId;
    private Long userId;
    private Integer paymentAmount;
    private String paymentMethod;
}