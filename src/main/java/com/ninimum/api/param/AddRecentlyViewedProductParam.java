package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddRecentlyViewedProductParam {
    private Long userId;
    private Long productId;
}