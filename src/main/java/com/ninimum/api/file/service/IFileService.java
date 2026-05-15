package com.ninimum.api.file.service;

import com.ninimum.api.dto.FileUploadDto;
import org.springframework.web.multipart.MultipartFile;

public interface IFileService {
    FileUploadDto uploadFile(MultipartFile file) throws Exception;
}