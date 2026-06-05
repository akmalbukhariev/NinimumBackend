package com.ninimum.api.param;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class RegisterUserParam {
    private Long region_id;
    private String first_name;
    private String last_name;
    private Double location_latitude;
    private Double location_longitude;
    private String address;
    private String phone_number;
    private String password;
    //private List<AddChildParam> children;
}
