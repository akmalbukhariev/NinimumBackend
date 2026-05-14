package com.ninimum.api.param;

import lombok.Data;

@Data
public class CreateQrParam {
    private Long userId;
    private String qrCode;
}