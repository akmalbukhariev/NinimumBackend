package com.ninimum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestPhoneDto {
    private Long id;
    private String phone;
    private String code;
    private boolean use;
}
