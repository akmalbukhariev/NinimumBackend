package com.ninimum.api.param;

import lombok.Data;

@Data
public class AddFavoriteParam {
    private Long user_id;
    private Long product_id;
}