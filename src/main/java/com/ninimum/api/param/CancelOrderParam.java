package com.ninimum.api.param;

import lombok.Data;

@Data
public class CancelOrderParam {
    private Long orderId;
    private Long userId;
}