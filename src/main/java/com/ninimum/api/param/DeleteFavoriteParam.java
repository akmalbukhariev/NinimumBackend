package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteFavoriteParam {
    private Long userId;
    private Long productId;
}