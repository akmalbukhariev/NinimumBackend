package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteAddressParam {
    private Long addressId;
    private Long userId;
}