package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class UpdateOrderPaymentStatusParam {
    private Long order_id;
    private String payment_status;
}