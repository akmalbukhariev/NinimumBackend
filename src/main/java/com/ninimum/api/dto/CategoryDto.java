package com.ninimum.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CategoryDto {
    private Long id;
    private Long parent_id;
    private String name;
    private String image_url;
    private Integer sort_order;
    private Boolean is_active;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
}