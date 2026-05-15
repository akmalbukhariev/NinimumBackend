package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddReportParam {
    private Long userId;
    private String reportType;
    private Long targetId;
    private String reason;
}