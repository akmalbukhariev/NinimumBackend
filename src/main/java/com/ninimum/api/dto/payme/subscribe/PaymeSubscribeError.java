package com.ninimum.api.dto.payme.subscribe;

import lombok.Data;

@Data
public class PaymeSubscribeError {
    private Integer code;
    private String message;
    private Object data;
}