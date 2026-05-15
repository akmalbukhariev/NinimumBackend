package com.ninimum.api.param;

import lombok.Data;

@Data
public class RefreshTokenParam {
    private Long userId;
    private String refreshToken;
}