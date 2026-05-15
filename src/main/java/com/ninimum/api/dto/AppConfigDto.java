package com.ninimum.api.dto;

import lombok.Data;

@Data
public class AppConfigDto {
    private Long configId;
    private String appVersion;
    private String forceUpdateYn;
    private String maintenanceYn;
    private String maintenanceMessage;
    private String termsUrl;
    private String privacyUrl;
}