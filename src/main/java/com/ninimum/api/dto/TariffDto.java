package com.ninimum.api.dto;

import lombok.Data;

@Data
public class TariffDto {
    private Long tariffId;
    private String tariffName;
    private Integer price;
    private Integer durationMonth;
    private String description;
}