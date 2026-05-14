package com.ninimum.api.dto;

import lombok.Data;

@Data
public class BannerDto {
    private Long bannerId;
    private String title;
    private String imageUrl;
    private String linkUrl;
}