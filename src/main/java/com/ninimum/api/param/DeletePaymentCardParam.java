package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeletePaymentCardParam {
    private Long cart_id;
    private Long user_id;
}