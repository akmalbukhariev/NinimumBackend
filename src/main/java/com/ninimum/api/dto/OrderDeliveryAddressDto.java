package com.ninimum.api.dto;

import lombok.Data;

@Data
public class OrderDeliveryAddressDto {
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
}
