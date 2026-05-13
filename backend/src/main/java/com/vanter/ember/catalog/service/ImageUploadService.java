package com.vanter.ember.catalog.service;

import com.vanter.ember.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.net.URI;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final long max_file_size = 5L * 1024 * 1024;
    private static final Set<String> allowed_types =
            Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public String uploadImage(MultipartFile file, String bucketName) {
        String contentType = file.getContentType();
        if (contentType == null || !allowed_types.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
        if (file.getSize() > max_file_size) {
            throw new IllegalArgumentException(
                    "File size exceeds maximum allowed size of 5MB");
        }

        String objectName = UUID.randomUUID() + extension(file.getOriginalFilename());
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(contentType)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
        return minioProperties.getUrl() + "/" + bucketName + "/" + objectName;
    }

    public void deleteImage(String imageUrl) {
        String path = URI.create(imageUrl).getPath();
        String[] parts = path.split("/", 3);
        String bucket = parts[1];
        String objectName = parts[2];
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete image: " + e.getMessage(), e);
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
}
