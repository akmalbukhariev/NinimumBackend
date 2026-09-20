package com.ninimum.api.param;

import lombok.Data;

@Data
public class CancelSubscriptionParam {
    private Long userId;
    private Long subscriptionId;
}
