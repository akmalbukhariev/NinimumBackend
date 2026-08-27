package com.ninimum.api.param;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderParam {
    private Long orderId;
    private Long userId;
    private Long addressId;
    private Integer subtotalPrice;
    private Integer discountPrice;
    private Integer totalPrice;
    private Long tariffSubscriptionId;
    private List<CreateOrderProductParam> products;
}