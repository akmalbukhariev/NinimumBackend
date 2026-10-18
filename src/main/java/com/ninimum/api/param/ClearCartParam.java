package com.ninimum.api.param;

import lombok.Data;

@Data
public class ClearCartParam {
    @com.fasterxml.jackson.annotation.JsonAlias("user_id")
    private Long userId;
}