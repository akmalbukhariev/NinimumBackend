package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteFavoriteParam {
    private Long user_id;
    private Long product_id;
}