package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreatePaymentCardParam {
    private Long user_id;

    // App sends this, but we DO NOT save it directly
    private String card_number;

    // Backend creates these values
    private String card_brand;
    private String card_hash;
    private String last_four_digits;
    private String payment_token;

    private String card_holder_name;
    private Integer expiry_month;
    private Integer expiry_year;

    // App sends this, but we DO NOT save it
    private String cvv;

    private Boolean is_default;
}