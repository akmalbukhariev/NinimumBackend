package com.ninimum.api.dto;

import lombok.Data;

@Data
public class DeliveryAppWorkerAuthDto {
    private Long id;
    private String workerId;
    private String fullName;
    private String phoneNumber;
    private String passwordHash;
    private String status;
    private Boolean online;
    private String vehicleType;
    private String vehicleNumber;
}
