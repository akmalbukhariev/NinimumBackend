package com.ninimum.api.dto;

import lombok.Data;

@Data
public class QrDto {
    private Long qrId;
    private Long userId;
    private String qrCode;
    private String qrStatus;
    private String createdDate;
}