package com.ninimum.api.dto;

import lombok.Data;

@Data
public class DeliveryAppWorkerDto {
    private Long id;
    private String workerId;
    private String fullName;
    private String phoneNumber;
    private String status;
    private Boolean online;
    private String vehicleType;
    private String vehicleNumber;
}
