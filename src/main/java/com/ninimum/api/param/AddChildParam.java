package com.ninimum.api.param;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class AddChildParam {
    private Long user_id;
    private String first_name;
    private String last_name;
    private String birth_date;
    private String gender;
}
