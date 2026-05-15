package com.ninimum.api.dto;

import lombok.Data;

@Data
public class UserCouponDto {
    private Long userCouponId;
    private Long userId;
    private Long couponId;
    private String couponName;
    private String couponType;
    private Integer discountAmount;
    private Integer discountRate;
    private String useYn;
    private String startDate;
    private String endDate;
}