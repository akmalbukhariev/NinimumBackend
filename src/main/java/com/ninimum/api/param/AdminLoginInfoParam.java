package com.ninimum.api.param;

import lombok.Data;

@Data
public class AdminLoginInfoParam {
    private String login_id;
    private String password;
}