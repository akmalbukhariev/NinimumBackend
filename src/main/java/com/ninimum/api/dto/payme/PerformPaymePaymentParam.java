package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class PerformPaymePaymentParam {
    private String transaction_id;
    private Long payme_perform_time;
}