package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteCartParam {
    private Long cartId;
    private Long userId;
}