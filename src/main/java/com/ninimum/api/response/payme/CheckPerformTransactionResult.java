package com.ninimum.api.response.payme;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class CheckPerformTransactionResult {
    private Boolean allow;
    private Map<String, Object> detail;

    public CheckPerformTransactionResult(Boolean allow) {
        this.allow = allow;
    }
}