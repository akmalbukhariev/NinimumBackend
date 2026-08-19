package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CancelTransactionResult {
    private String transaction;
    private Long cancel_time;
    private Integer state;
}