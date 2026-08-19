package com.ninimum.api.dto.payme;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymeError {
    private Integer code;
    private String message;
    private Object data;
}