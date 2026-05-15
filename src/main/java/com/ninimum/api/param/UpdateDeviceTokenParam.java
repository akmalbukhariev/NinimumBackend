package com.ninimum.api.param;

import lombok.Data;

@Data
public class UpdateDeviceTokenParam {
    private Long userId;
    private String deviceToken;
    private String osType;
}