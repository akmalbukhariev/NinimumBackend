package com.ninimum.api.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderDto {
    private Long orderId;
    private Long userId;
    private String orderNumber;
    private String status;
    private String paymentStatus;
    private BigDecimal totalPrice;
    private Integer productCount;
    private String orderedAt;
    private String partialReturnStatus;
    private String deliveryMethod;
    private String taxiCompany;
    private String taxiDriverName;
    private String taxiDriverPhone;
    private String taxiCarNumber;
    private java.math.BigDecimal taxiFare;
    /** Courier GPS V1a: ASK (show "Mahsulotni oldim / Muammo bor"), RECEIVED, PROBLEM or null. */
    private String deliveryConfirm;
    private BigDecimal partialRefundAmount;
}
