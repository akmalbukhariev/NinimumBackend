package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreateSubscriptionParam {
    private Long subscriptionId;
    private Long userId;
    private Long tariffId;
    private String startDate;
    private String endDate;
}