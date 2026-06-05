package com.ninimum.api.param;

import lombok.Data;

@Data
public class RegisterAdminParam {
    private String login_id;
    private String password;
    private String name;
}