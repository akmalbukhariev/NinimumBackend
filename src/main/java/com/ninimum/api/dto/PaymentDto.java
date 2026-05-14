package com.ninimum.api.dto;

import lombok.Data;

@Data
public class PaymentDto {
    private Long paymentId;
    private Long orderId;
    private Long userId;
    private Integer paymentAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String paymentDate;
}