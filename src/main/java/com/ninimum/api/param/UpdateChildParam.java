package com.ninimum.api.param;

import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class UpdateChildParam {
    private Long childId;
    private Long userId;
    private String firstName;
    private String lastName;
    private String birthDate;
    private String gender;
}
