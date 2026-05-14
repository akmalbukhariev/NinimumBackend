package com.ninimum.api.param;

import lombok.Data;

@Data
public class DeleteChildParam {
    private Long childId;
    private Long userId;
}