package com.ninimum.api.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class DeliveryAppJobDto {
    private Long jobId;
    private Long orderId;
    private String orderNumber;
    private String customerName;
    private String customerPhone;
    private String deliveryAddress;
    private Double locationLatitude;
    private Double locationLongitude;
    private String jobStatus;
    private String orderStatus;
    private String paymentStatus;
    private BigDecimal totalPrice;
    private Integer productCount;
    private String createdAt;
    private String acceptedAt;
    private String onTheWayAt;
    private String deliveredAt;
    private String note;
    private List<DeliveryAppJobProductDto> products = new ArrayList<>();
}
