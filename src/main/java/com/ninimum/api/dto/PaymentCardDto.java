package com.ninimum.api.dto;

import lombok.Data;

@Data
public class PaymentCardDto {
    private Long id;
    private Long user_id;
    private String card_holder_name;
    private String card_brand;
    private String last_four_digits;
    private Integer expiry_month;
    private Integer expiry_year;
    private String payment_token;
    private Boolean is_default;
    private String created_at;
    private String updated_at;
}