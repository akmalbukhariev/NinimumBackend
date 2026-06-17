package com.ninimum.api.file.service.impl;

import com.ninimum.api.dto.FileUploadDto;
import com.ninimum.api.file.service.IFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class FileService implements IFileService {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Override
    public FileUploadDto uploadFile(MultipartFile file) throws Exception {
        String filePath = saveProductImage(file);

        FileUploadDto dto = new FileUploadDto();
        dto.setFileUrl(filePath);

        return dto;
    }

    @Override
    public String saveProductImage(MultipartFile file) throws Exception {

        String originalName = file.getOriginalFilename();
        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID() + extension;

        Path productDir = Path.of(uploadPath, "products");
        Files.createDirectories(productDir);

        Path savePath = productDir.resolve(fileName);
        file.transferTo(savePath.toFile());

        return "products/" + fileName;
    }

    @Override
    public String saveReviewImage(MultipartFile file) throws Exception {

        String originalName = file.getOriginalFilename();
        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID() + extension;

        Path reviewDir = Path.of(uploadPath, "reviews");
        Files.createDirectories(reviewDir);

        Path savePath = reviewDir.resolve(fileName);
        file.transferTo(savePath.toFile());

        return "reviews/" + fileName;
    }
}