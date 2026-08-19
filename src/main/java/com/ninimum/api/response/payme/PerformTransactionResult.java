package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PerformTransactionResult {
    private String transaction;
    private Long perform_time;
    private Integer state;
}