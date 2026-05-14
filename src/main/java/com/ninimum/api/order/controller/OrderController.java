package com.ninimum.api.order.controller;

import com.ninimum.api.common.BaseController;
import com.ninimum.api.common.Result;
import com.ninimum.api.common.VersionResponseResult;
import com.ninimum.api.constants.Constant;
import com.ninimum.api.dto.OrderDetailDto;
import com.ninimum.api.dto.OrderDto;
import com.ninimum.api.order.service.IOrderService;
import com.ninimum.api.param.CreateOrderParam;
import com.ninimum.api.param.OrderDetailParam;
import com.ninimum.api.param.OrderListParam;
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
@Tag(name = "mapper/Order", description = "Order APIs.")
@RequestMapping(value={"/samokat/api/v1/order"})
public class OrderController extends BaseController {

    private final IOrderService orderService;

    @PostConstruct
    public void init() {
        setApiVersion(Constant.api_version);
    }

    @Operation(
            tags = {"mapper/Order"},
            summary = "1. Order list",
            description = "Returns order list by user ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getOrderList", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getOrderList(@RequestBody OrderListParam param) {
        VersionResponseResult result = null;

        try {
            List<OrderDto> orders = this.orderService.getOrderList(param);
            result = this.setResult(Result.SUCCESS, orders);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("OrderController => getOrderList: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"mapper/Order"},
            summary = "2. Order detail",
            description = "Returns order detail by order ID.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/getOrderDetail", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> getOrderDetail(@RequestBody OrderDetailParam param) {
        VersionResponseResult result = null;

        try {
            List<OrderDetailDto> orderDetails = this.orderService.getOrderDetail(param);
            result = this.setResult(Result.SUCCESS, orderDetails);
        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("OrderController => getOrderDetail: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @Operation(
            tags = {"mapper/Order"},
            summary = "3. Create order",
            description = "Creates a new order with order detail products.",
            hidden = false,
            responses = { @ApiResponse(responseCode = "200", description = "success") },
            security = { @SecurityRequirement(name = "bearerAuth") }
    )
    @PostMapping(value = "/createOrder", headers = { "Content-type=application/json" })
    public ResponseEntity<Object> createOrder(@RequestBody CreateOrderParam param) {
        VersionResponseResult result = null;

        try {
            int resultNum = this.orderService.createOrder(param);

            if (resultNum != 0) {
                result = this.setResult(Result.SUCCESS);
            } else {
                result = this.setResult(Result.SERVER_ERROR);
            }

        } catch (Exception ex) {
            result = this.setResult(Result.SERVER_ERROR);
            log.error("OrderController => createOrder: ", ex);
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }
}