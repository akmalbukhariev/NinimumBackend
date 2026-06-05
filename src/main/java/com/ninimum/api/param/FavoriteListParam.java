package com.ninimum.api.param;

import lombok.Data;

@Data
public class FavoriteListParam extends PageSizeParam {
    private Long user_id;
}