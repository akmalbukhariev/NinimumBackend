package com.ninimum.api.param;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class ChangeRegionParam {
    private Long userId;
    private Long regionId;
}