package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class UpdateSubscriptionStatusParam {
    private Long subscription_id;
    private String subscription_status;
}
