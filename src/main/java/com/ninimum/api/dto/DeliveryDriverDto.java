package com.ninimum.api.dto;

import lombok.Data;

@Data
public class DeliveryDriverDto {
    private Long driverId;
    private String driverName;
    private String driverPhone;
    private String driverStatus;
}