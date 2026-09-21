package com.ninimum.api.dto;

import lombok.Data;

@Data
public class DeliveryAppDashboardDto {
    private Integer availableCount;
    private Integer activeCount;
    private Integer completedTodayCount;
}
