package com.ninimum.api.dto;

import lombok.Data;

@Data
public class OrderProcessDto {
    private Long orderId;
    private String orderNumber;
    private String status;
    private String paymentStatus;
    private String orderedAt;
    private String deliveredAt;
    private String partialReturnStatus;
    private String deliveryMethod;
    private String taxiCompany;
    private String taxiDriverName;
    private String taxiDriverPhone;
    private String taxiCarNumber;
    private java.math.BigDecimal taxiFare;
    /** Courier GPS V1a: ASK (show "Mahsulotni oldim / Muammo bor"), RECEIVED, PROBLEM or null. */
    private String deliveryConfirm;
}
