package com.ninimum.api.dto;

import lombok.Data;

@Data
public class DeliveryDto {
    private Long deliveryId;
    private Long orderId;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String deliveryAddress;
    private String deliveryStatus;
    private String deliveryDate;
}