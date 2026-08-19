package com.ninimum.api.dto.payme;

import lombok.Data;

@Data
public class CancelPaymePaymentParam {
    private String transaction_id;
    private Long payme_cancel_time;
    private Integer payme_reason;
    private String status;
}