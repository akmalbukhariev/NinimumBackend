package com.ninimum.api.dto;

import lombok.Data;

@Data
public class ReportDto {
    private Long reportId;
    private Long userId;
    private String reportType;
    private Long targetId;
    private String reason;
    private String reportStatus;
    private String createdDate;
}