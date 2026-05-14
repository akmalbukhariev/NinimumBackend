package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddFavoriteParam {
    private Long userId;
    private Long productId;
}