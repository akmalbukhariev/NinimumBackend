package com.ninimum.api.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class VersionResponseResult extends ResponseResult {
    private String apiVersion;
    private String webVersion;
    // Explicit setter method
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
}