package com.ninimum.api.param;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class RegisterUserParam {
    private Long region_id;
    private String first_name;
    private String last_name;
    private String phone_number;
    private String password;
}
