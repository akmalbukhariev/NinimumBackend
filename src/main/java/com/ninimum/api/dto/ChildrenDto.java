package com.ninimum.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChildrenDto {
    private Long id;
    private Long user_id;
    private String first_name;
    private String last_name;
    private LocalDate birth_date;
    private String gender;
    private Boolean is_active;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}
