package com.ninimum.api.dto.payme;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreatePaymePaymentParam {
    private Long order_id;
    private Long subscription_id;
    private Long user_id;

    private String provider_transaction_id;

    private BigDecimal amount;
    private Long amount_tiyin;

    private Long payme_time;
    private Long payme_create_time;
}