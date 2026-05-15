package com.ninimum.api.dto;

import lombok.Data;

@Data
public class FileUploadDto {
    private String originalFileName;
    private String savedFileName;
    private String fileUrl;
    private Long fileSize;
}