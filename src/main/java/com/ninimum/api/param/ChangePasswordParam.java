package com.ninimum.api.param;

import lombok.Data;

@Data
public class ChangePasswordParam {
    private Long userId;
    private String currentPassword;
    private String newPassword;
}