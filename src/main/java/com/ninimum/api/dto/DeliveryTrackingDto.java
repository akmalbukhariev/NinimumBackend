package com.ninimum.api.dto;

import lombok.Data;

@Data
public class DeliveryTrackingDto {
    private Long trackingId;
    private Long deliveryId;
    private String trackingStatus;
    private String trackingMessage;
    private String createdDate;
}