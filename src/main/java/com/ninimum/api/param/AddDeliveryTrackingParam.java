package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddDeliveryTrackingParam {
    private Long deliveryId;
    private String trackingStatus;
    private String trackingMessage;
}