package com.ninimum.api.param;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderParam {
    private Long orderId;
    private Long userId;
    private Long addressId;
    private String deliveryAddress;
    private Double deliveryLatitude;
    private Double deliveryLongitude;
    private Integer subtotalPrice;
    private Integer discountPrice;
    private Integer totalPrice;
    private Integer quotedTotal;
    private Long tariffSubscriptionId;
    /** "COURIER" (default) or "TAXI": fast delivery by an outside taxi, the customer pays the driver. */
    private String deliveryMethod;
    private List<CreateOrderProductParam> products;
}