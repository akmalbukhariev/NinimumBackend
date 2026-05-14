package com.ninimum.api.dto;

import lombok.Data;

@Data
public class PartnerDto {
    private Long partnerId;
    private String partnerName;
    private String partnerLogoUrl;
    private String partnerAddress;
    private String partnerPhone;
    private String description;
}