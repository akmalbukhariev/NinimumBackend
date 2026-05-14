package com.ninimum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegionDto {
    private Long id;
    private String name;
    private Boolean isActive;
}
