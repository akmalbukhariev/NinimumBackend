package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreatePaymeCheckoutUrlResponse {
    private String payment_url;
}