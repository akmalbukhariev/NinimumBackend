package com.ninimum.api.dto;

import lombok.Data;

@Data
public class AddressDto {
    private Long addressId;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String detailAddress;
    private String defaultYn;
}