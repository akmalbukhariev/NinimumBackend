package com.ninimum.api.param;

import lombok.Data;

@Data
public class UpdateDeliveryStatusParam {
    private Long deliveryId;
    private String deliveryStatus;
}