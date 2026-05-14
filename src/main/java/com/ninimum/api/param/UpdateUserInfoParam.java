package com.ninimum.api.param;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UpdateUserInfoParam {

    private Long id;

    private String first_name;

    private String last_name;

    private String email;

    private String gender;
}
