package com.ninimum.api.coupon.service;

import com.ninimum.api.dto.CouponDto;
import com.ninimum.api.dto.UserCouponDto;
import com.ninimum.api.param.AddUserCouponParam;
import com.ninimum.api.param.UserCouponListParam;

import java.util.List;

public interface ICouponService {
    List<CouponDto> getCouponList() throws Exception;

    List<UserCouponDto> getUserCouponList(UserCouponListParam param) throws Exception;

    int addUserCoupon(AddUserCouponParam param) throws Exception;
}