package com.vanter.ember.catalog.service;

import com.vanter.ember.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    @Mock MinioClient minioClient;
    @Mock MinioProperties minioProperties;
    @InjectMocks ImageUploadService imageUploadService;

    @Test
    void uploadImage_returnsUrlUnderConfiguredPublicBase() throws Exception {
        when(minioProperties.getPublicUrl()).thenReturn("https://cdn.example.test");
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", buffer.toByteArray());

        String url = imageUploadService.uploadImage(file);

        assertThat(url).startsWith("https://cdn.example.test/");
        assertThat(url).endsWith(".jpg");
        assertThat(url).doesNotContain("ember-media-prod"); // no bucket segment in the public URL
    }

    @Test
    void uploadImage_setsImmutableCacheControlHeader() throws Exception {
        when(minioProperties.getPublicUrl()).thenReturn("https://cdn.example.test");
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", buffer);
        MockMultipartFile file = new MockMultipartFile(
                "image", "photo.jpg", "image/jpeg", buffer.toByteArray());

        imageUploadService.uploadImage(file);

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertThat(captor.getValue().headers().get("Cache-Control"))
                .containsExactly("public, max-age=31536000, immutable");
    }

    @Test
    void uploadImage_throwsForInvalidMimeType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> imageUploadService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
    }

    @Test
    void uploadImage_throwsWhenFileTooLarge() {
        byte[] largeContent = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "image", "big.jpg", "image/jpeg", largeContent);

        assertThatThrownBy(() -> imageUploadService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds maximum");
    }

    @Test
    void deleteImage_usesLastPathSegmentAndConfiguredBucket() throws Exception {
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");

        imageUploadService.deleteImage("https://cdn.example.test/uuid-abc.jpg");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo("uuid-abc.jpg");
        assertThat(captor.getValue().bucket()).isEqualTo("ember-media-prod");
    }

    @Test
    void deleteImage_ignoresNullOrBlank() throws Exception {
        imageUploadService.deleteImage(null);
        imageUploadService.deleteImage("  ");
        // no interaction with minioClient
    }
}
