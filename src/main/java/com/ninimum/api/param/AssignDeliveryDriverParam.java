package com.ninimum.api.param;

import lombok.Data;

@Data
public class AssignDeliveryDriverParam {
    private Long deliveryId;
    private Long driverId;
}