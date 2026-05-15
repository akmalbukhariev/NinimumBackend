package com.ninimum.api.coupon.service.impl;

import com.ninimum.api.coupon.service.CouponMapper;
import com.ninimum.api.coupon.service.ICouponService;
import com.ninimum.api.dto.CouponDto;
import com.ninimum.api.dto.UserCouponDto;
import com.ninimum.api.param.AddUserCouponParam;
import com.ninimum.api.param.UserCouponListParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService implements ICouponService {

    private final CouponMapper couponMapper;

    @Override
    public List<CouponDto> getCouponList() throws Exception {
        return this.couponMapper.getCouponList();
    }

    @Override
    public List<UserCouponDto> getUserCouponList(UserCouponListParam param) throws Exception {
        return this.couponMapper.getUserCouponList(param);
    }

    @Override
    public int addUserCoupon(AddUserCouponParam param) throws Exception {
        return this.couponMapper.addUserCoupon(param);
    }
}