package com.ninimum.api.dto;

import lombok.Data;

@Data
public class AdminDto {
    private Long id;
    private String login_id;
    private String password;
    private String name;
    private String role;
    private String status;
}