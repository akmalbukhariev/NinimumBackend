package com.ninimum.api.dto;

import lombok.Data;

@Data
public class CouponDto {
    private Long couponId;
    private String couponName;
    private String couponType;
    private Integer discountAmount;
    private Integer discountRate;
    private String startDate;
    private String endDate;
}