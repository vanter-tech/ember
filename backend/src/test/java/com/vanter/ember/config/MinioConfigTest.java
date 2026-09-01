package com.vanter.ember.config;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MinioConfigTest {

    @Autowired MinioProperties minioProperties;
    @Autowired MinioClient minioClient;
    @Autowired ApplicationRunner ensureBucketExists;

    @Test
    void minioPropertiesBindFromConfig() {
        assertThat(minioProperties.getUrl()).isEqualTo("http://localhost:9000");
        assertThat(minioProperties.getAccessKey()).isEqualTo("minioadmin");
        assertThat(minioProperties.getSecretKey()).isEqualTo("minioadmin");
        assertThat(minioProperties.getBucket()).isEqualTo("ember-media");
        assertThat(minioProperties.isManageBucket()).isTrue();
    }

    @Test
    void minioClientBeanIsCreated() {
        assertThat(minioClient).isNotNull();
    }

    @Test
    void bucketRunnerBeanIsRegistered() {
        assertThat(ensureBucketExists).isNotNull();
    }
}
