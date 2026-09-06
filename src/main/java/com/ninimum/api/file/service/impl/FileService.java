package com.ninimum.api.file.service.impl;

import com.ninimum.api.dto.FileUploadDto;
import com.ninimum.api.file.service.IFileService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileService implements IFileService {

    private static final int REVIEW_IMAGE_MAX_WIDTH = 1024;
    private static final int REVIEW_IMAGE_MAX_HEIGHT = 1024;
    private static final float REVIEW_IMAGE_JPEG_QUALITY = 0.80f;

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
        validateReviewImage(file);

        // Mobile already normalizes camera EXIF orientation and uploads JPEG, but the backend
        // resizes/re-encodes again so the storage rule is enforced even for another client.
        BufferedImage source = ImageIO.read(file.getInputStream());
        if (source == null) {
            throw new IllegalArgumentException("Unsupported review image format.");
        }

        BufferedImage resized = resizeReviewImage(source, REVIEW_IMAGE_MAX_WIDTH, REVIEW_IMAGE_MAX_HEIGHT);
        String fileName = UUID.randomUUID() + ".jpg";

        Path reviewDir = Path.of(uploadPath, "reviews");
        Files.createDirectories(reviewDir);

        Path savePath = reviewDir.resolve(fileName);
        writeJpeg(resized, savePath, REVIEW_IMAGE_JPEG_QUALITY);

        if (resized != source) {
            resized.flush();
        }
        source.flush();

        return "reviews/" + fileName;
    }

    private BufferedImage resizeReviewImage(BufferedImage source, int maxWidth, int maxHeight) {
        int originalWidth = source.getWidth();
        int originalHeight = source.getHeight();
        double scale = Math.min(1.0, Math.min(
                (double) maxWidth / originalWidth,
                (double) maxHeight / originalHeight));

        int width = Math.max(1, (int) Math.round(originalWidth * scale));
        int height = Math.max(1, (int) Math.round(originalHeight * scale));

        // Always draw into RGB because review uploads are stored as JPEG.
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }

        return output;
    }

    private void writeJpeg(BufferedImage image, Path savePath, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("JPEG writer is not available.");
        }

        ImageWriter writer = writers.next();
        try (OutputStream outputStream = Files.newOutputStream(savePath);
             ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(imageOutputStream);

            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            if (writeParam.canWriteCompressed()) {
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionQuality(Math.max(0.01f, Math.min(1.0f, quality)));
            }

            writer.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            writer.dispose();
        }
    }

    private void validateReviewImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Review image is empty.");
        }

        if (file.getSize() > 10L * 1024L * 1024L) {
            throw new IllegalArgumentException("Review image is too large.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only image files can be uploaded for a review.");
        }
    }

    @Override
    public void deleteReviewImage(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }

        try {
            Path reviewDir = Path.of(uploadPath, "reviews").toAbsolutePath().normalize();
            String fileName = Path.of(relativePath).getFileName().toString();
            Path filePath = reviewDir.resolve(fileName).normalize();

            if (filePath.startsWith(reviewDir)) {
                Files.deleteIfExists(filePath);
            }
        } catch (Exception ignored) {
            // Do not fail review editing if an old image file is already missing.
        }
    }
}
