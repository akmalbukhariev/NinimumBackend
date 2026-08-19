package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateTransactionResult {
    private Long create_time;
    private String transaction;
    private Integer state;
}