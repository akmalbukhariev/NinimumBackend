package com.ninimum.api.file.service.impl;

import com.ninimum.api.dto.FileUploadDto;
import com.ninimum.api.file.service.IFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.UUID;

@Service
public class FileService implements IFileService {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Value("${file.upload.url}")
    private String uploadUrl;

    @Override
    public FileUploadDto uploadFile(MultipartFile file) throws Exception {
        File folder = new File(uploadPath);

        if (!folder.exists()) {
            folder.mkdirs();
        }

        String originalFileName = file.getOriginalFilename();
        String extension = "";

        if (originalFileName != null && originalFileName.contains(".")) {
            extension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        String savedFileName = UUID.randomUUID().toString() + extension;
        File saveFile = new File(uploadPath, savedFileName);

        file.transferTo(saveFile);

        FileUploadDto dto = new FileUploadDto();
        dto.setOriginalFileName(originalFileName);
        dto.setSavedFileName(savedFileName);
        dto.setFileUrl(uploadUrl + "/" + savedFileName);
        dto.setFileSize(file.getSize());

        return dto;
    }
}