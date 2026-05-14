package com.ninimum.api.param;

import lombok.Data;

@Data
public class UpdateAddressParam {
    private Long addressId;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String address;
    private String detailAddress;
    private String defaultYn;
}