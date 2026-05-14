package com.ninimum.api.dto;

import lombok.Data;

@Data
public class SubscriptionDto {
    private Long subscriptionId;
    private Long userId;
    private Long tariffId;
    private String tariffName;
    private Integer price;
    private String startDate;
    private String endDate;
    private String subscriptionStatus;
}