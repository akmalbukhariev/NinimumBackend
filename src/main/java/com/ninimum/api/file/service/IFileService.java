package com.ninimum.api.file.service;

import com.ninimum.api.dto.FileUploadDto;
import org.springframework.web.multipart.MultipartFile;

public interface IFileService {
    FileUploadDto uploadFile(MultipartFile file) throws Exception;
    String saveProductImage(MultipartFile file) throws Exception;
    String saveProductImage(byte[] bytes, String originalName) throws Exception;
    String saveReviewImage(MultipartFile file) throws Exception;
    void deleteReviewImage(String relativePath);
    void deleteProductImage(String relativePath);
}