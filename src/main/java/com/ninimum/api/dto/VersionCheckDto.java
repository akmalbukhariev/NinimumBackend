package com.ninimum.api.dto;

import lombok.Data;

@Data
public class VersionCheckDto {
    private String latestVersion;
    private String forceUpdateYn;
    private String updateMessage;
    private String appStoreUrl;
    private String playStoreUrl;
}