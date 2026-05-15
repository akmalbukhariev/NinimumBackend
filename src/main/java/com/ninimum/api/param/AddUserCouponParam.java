package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddUserCouponParam {
    private Long userId;
    private Long couponId;
}