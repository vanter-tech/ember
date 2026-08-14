package com.vanter.ember.catalog.service;

import com.vanter.ember.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    @Mock MinioClient minioClient;
    @Mock MinioProperties minioProperties;
    @InjectMocks ImageUploadService imageUploadService;

    @Test
    void uploadImage_returnsPublicUrl() throws Exception {
        when(minioProperties.getUrl()).thenReturn("http://localhost:9000");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", buffer.toByteArray());

        String url = imageUploadService.uploadImage(file, "ember-media");

        assertThat(url).startsWith("http://localhost:9000/ember-media/");
        assertThat(url).endsWith(".jpg");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadImage_throwsForInvalidMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> imageUploadService.uploadImage(file, "ember-media"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void uploadImage_throwsWhenFileTooLarge() {
        byte[] largeContent = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", largeContent);

        assertThatThrownBy(() -> imageUploadService.uploadImage(file, "ember-media"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void deleteImage_callsRemoveObject() throws Exception {
        imageUploadService.deleteImage("http://localhost:9000/ember-media/uuid-abc.jpg");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }
}
