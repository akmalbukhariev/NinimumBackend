package com.ninimum.api.file.service;

import com.ninimum.api.dto.FileUploadDto;
import org.springframework.web.multipart.MultipartFile;

public interface IFileService {
    FileUploadDto uploadFile(MultipartFile file) throws Exception;
    String saveProductImage(MultipartFile file) throws Exception;
    String saveReviewImage(MultipartFile file) throws Exception;
}