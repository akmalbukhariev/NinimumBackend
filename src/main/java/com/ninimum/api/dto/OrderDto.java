package com.ninimum.api.dto;

import lombok.Data;

@Data
public class OrderDto {
    private Long orderId;
    private Long userId;
    private Integer totalPrice;
    private String orderStatus;
    private String orderDate;
}