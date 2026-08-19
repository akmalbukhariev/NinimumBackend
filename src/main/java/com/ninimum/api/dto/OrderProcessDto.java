package com.ninimum.api.dto;

import lombok.Data;

@Data
public class OrderProcessDto {
    private Long orderId;
    private String orderNumber;
    private String status;
    private String orderedAt;
    private String deliveredAt;
}
