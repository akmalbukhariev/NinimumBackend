package com.ninimum.api.response;

import lombok.Data;

@Data
public class EskizLoginResponse {
    private String message;
    private TokenData data;
    private String token_type;
}