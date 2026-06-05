package com.ninimum.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ninimum.api.constants.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDto {
    private Long id;
    private Long region_id;

    private String first_name;
    private String last_name;
    private String phone_number;

    private Double location_latitude;
    private Double location_longitude;
    private String address;

    private String email;
    private String password;
    private String profile_image_url;

    private LocalDate birth_date;
    private String gender;

    private Boolean is_phone_verified;
    private Boolean is_active;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime created_at;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updated_at;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime blocked_until;

    private String token_mb;
    private UserStatus status;
}


