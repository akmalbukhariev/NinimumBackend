package com.ninimum.api.dto;

import lombok.Data;

@Data
public class RefreshTokenDto {
    private String accessToken;
    private String refreshToken;
}