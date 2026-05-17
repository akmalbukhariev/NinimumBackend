package com.ninimum.api.coupon.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.coupon.service.ICouponService;
import com.ninimum.api.dto.CouponDto;
import com.ninimum.api.dto.UserCouponDto;
import com.ninimum.api.param.AddUserCouponParam;
import com.ninimum.api.param.UserCouponListParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Coupon", description = "Coupon APIs.")
@RequestMapping(value={"/ninimum/api/v1/coupon"})
public class CouponController extends BaseController {

    private final ICouponService couponService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"Coupon"},
            summary = "1. Coupon list",
            description = "Returns available coupon list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @GetMapping(value = "/getCouponList")
    public ResponseEntity<Object> getCouponList() {
        VersionResponseResult result = null;

        try {
            List<CouponDto> coupons = this.couponService.getCouponList();
            result = this.setResult(Result.SUCCESS, coupons);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CouponController => getCouponList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Coupon"},
            summary = "2. My coupon list",
            description = "Returns user coupon list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getUserCouponList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getUserCouponList(@RequestBody UserCouponListParam param) {
        VersionResponseResult result = null;

        try {
            List<UserCouponDto> coupons = this.couponService.getUserCouponList(param);
            result = this.setResult(Result.SUCCESS, coupons);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CouponController => getUserCouponList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"Coupon"},
            summary = "3. Add user coupon",
            description = "Adds coupon to user coupon list.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/addUserCoupon", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> addUserCoupon(@RequestBody AddUserCouponParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.couponService.addUserCoupon(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("CouponController => addUserCoupon: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}