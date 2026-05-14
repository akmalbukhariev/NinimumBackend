package com.ninimum.api.param;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UserLoginInfoParam {
    private String phone_number;
    @JsonIgnore
    private String password;
}
