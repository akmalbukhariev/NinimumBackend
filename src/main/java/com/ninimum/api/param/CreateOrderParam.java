package com.ninimum.api.param;

import lombok.Data;
import java.util.List;

@Data
public class CreateOrderParam {
    private Long orderId;
    private Long userId;
    private Integer totalPrice;
    private List<CreateOrderProductParam> products;
}