package com.vanter.ember.catalog.controller;

import com.vanter.ember.config.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Serves menu and category photos from the private media bucket. Diners are anonymous, so the
 * route is public, but only names {@code ImageUploadService} generates ({@code <uuid>.jpg}) match:
 * nothing else in the bucket (ticket logos, anything an operator drops in) can be requested, and
 * the bucket itself can stay closed to the internet.
 */
@Slf4j
@RestController
@RequestMapping("/public/media")
@RequiredArgsConstructor
public class PublicMediaController {

    private static final String UUID_JPG =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg";

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @GetMapping("/{name:" + UUID_JPG + "}")
    public ResponseEntity<byte[]> get(
            @PathVariable String name,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        try (GetObjectResponse object = minioClient.getObject(
                GetObjectArgs.builder().bucket(minioProperties.getBucket()).object(name).build())) {
            String etag = object.headers().get(HttpHeaders.ETAG);
            CacheControl cache = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();
            if (etag != null && etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(cache).build();
            }
            ResponseEntity.BodyBuilder ok = ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(cache);
            if (etag != null) {
                ok.header(HttpHeaders.ETAG, etag);
            }
            return ok.body(object.readAllBytes());
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return ResponseEntity.notFound().cacheControl(CacheControl.noStore()).build();
            }
            return badGateway(name, e);
        } catch (IOException | java.security.GeneralSecurityException | io.minio.errors.MinioException e) {
            return badGateway(name, e);
        }
    }

    private ResponseEntity<byte[]> badGateway(String name, Exception e) {
        log.warn("Could not read media object {}: {}", name, e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).cacheControl(CacheControl.noStore()).build();
    }
}
