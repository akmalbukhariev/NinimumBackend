package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreateDeliveryAppWorkerParam {
    private String workerId;
    private String fullName;
    private String phoneNumber;
    private String password;
    private String vehicleType;
    private String vehicleNumber;
}
