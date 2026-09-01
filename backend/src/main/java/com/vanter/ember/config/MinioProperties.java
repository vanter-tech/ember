package com.vanter.ember.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

    private String url;
    private String accessKey;
    private String secretKey;
    private String bucket;
    /** Public base a browser fetches images from (CDN, custom domain, or the bucket URL); the object name is appended. */
    private String publicUrl;
    /**
     * Whether the app should create the bucket and (re)apply a public-read policy on boot.
     * True for local MinIO / the portable Hub. False against Google Cloud Storage's S3 XML
     * API in prod: the bucket is provisioned by the operator (HPD-16) and GCS has no
     * S3 {@code PutBucketPolicy} endpoint, so the attempt only logs a WARN every boot.
     */
    private boolean manageBucket = true;
}
