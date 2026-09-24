package com.vanter.ember.printing.logo;

import com.vanter.ember.config.MinioProperties;
import com.vanter.ember.settings.model.SettingsPayload;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores one receipt logo per restaurant in object storage (key derived from the tenant id only,
 * never from client input) and serves the printer-ready bitmap. Existence of the object is the
 * "has a logo" flag, so there is no settings field a client could tamper with.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketLogoService {

    private static final long MAX_UPLOAD_BYTES = 2L * 1024 * 1024;
    /** Declared types accepted up front; the file signature is what is actually trusted (see the processor). */
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif");

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    static String objectKey(UUID tenantId) {
        return "ticket-logos/" + tenantId + ".png";
    }

    public void store(UUID tenantId, MultipartFile file) {
        byte[] normalized = validateAndNormalize(tenantId, file);
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey(tenantId))
                    .stream(new ByteArrayInputStream(normalized), normalized.length, -1)
                    .contentType("image/png")
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to store the logo: " + e.getMessage(), e);
        }
    }

    /**
     * Everything that decides whether an upload is accepted. The client's file name and declared
     * type are never trusted or stored: the size is bounded, the declared type must be an image
     * type, and the processor then verifies the real signature, bounds the dimensions and
     * re-encodes the pixels. A rejection is logged (tenant and reason only, never the content).
     */
    private byte[] validateAndNormalize(UUID tenantId, MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Logo file is required");
            }
            if (file.getSize() > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("Logo must be 2MB or smaller");
            }
            String declared = file.getContentType();
            if (declared != null && !ALLOWED_CONTENT_TYPES.contains(declared.toLowerCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("Unsupported image type (use PNG, JPG or GIF)");
            }
            try {
                return TicketLogoProcessor.normalize(file.getBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read the uploaded file", e);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Ticket logo upload rejected for tenant {}: {}", tenantId, e.getMessage());
            throw e;
        }
    }

    public void delete(UUID tenantId) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey(tenantId))
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete the logo: " + e.getMessage(), e);
        }
    }

    /** Best effort: any storage error reads as "no logo" so a print job is never blocked by it. */
    public boolean exists(UUID tenantId) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(minioProperties.getBucket())
                    .object(objectKey(tenantId))
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** The stored logo dithered to 1-bit for the given paper width, or empty when none is stored. */
    public Optional<byte[]> loadBitonal(UUID tenantId, SettingsPayload.PaperWidth paperWidth) {
        byte[] stored;
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket())
                .object(objectKey(tenantId))
                .build())) {
            stored = in.readAllBytes();
        } catch (Exception e) {
            return Optional.empty();
        }
        int width = paperWidth == SettingsPayload.PaperWidth.MM_58
                ? TicketLogoProcessor.WIDTH_58MM_DOTS
                : TicketLogoProcessor.WIDTH_80MM_DOTS;
        return Optional.of(TicketLogoProcessor.toBitonalPng(stored, width));
    }
}
