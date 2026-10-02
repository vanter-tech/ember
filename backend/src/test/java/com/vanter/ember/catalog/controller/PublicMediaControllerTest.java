package com.vanter.ember.catalog.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.MinioProperties;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.identity.repository.UserRepository;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import java.io.ByteArrayInputStream;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PublicMediaController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class PublicMediaControllerTest {

    private static final String NAME = "0b6f1c2e-3d4a-4f5b-8c9d-1e2f3a4b5c6d.jpg";
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3};

    @Autowired MockMvc mockMvc;
    @MockBean MinioClient minioClient;
    @MockBean MinioProperties minioProperties;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean UserRepository userRepository;
    @MockBean RestaurantRepository restaurantRepository;

    @BeforeEach
    void bucket() {
        when(minioProperties.getBucket()).thenReturn("ember-media-prod");
    }

    private GetObjectResponse object() {
        return new GetObjectResponse(
                Headers.of("ETag", "\"abc\""), "ember-media-prod", "", NAME, new ByteArrayInputStream(JPEG));
    }

    @Test
    void servesTheImageWithoutAuthAndWithLongImmutableCache() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(object());

        mockMvc.perform(get("/public/media/" + NAME))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPEG))
                .andExpect(header().string("ETag", "\"abc\""))
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"));
    }

    @Test
    void answers304WhenTheEtagMatches() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(object());

        mockMvc.perform(get("/public/media/" + NAME).header("If-None-Match", "\"abc\""))
                .andExpect(status().isNotModified());
    }

    @Test
    void missingObjectIs404AndNeverCached() throws Exception {
        ErrorResponse noSuchKey = new ErrorResponse("NoSuchKey", "", "ember-media-prod", NAME, "", "", "");
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new ErrorResponseException(noSuchKey, null, null));

        mockMvc.perform(get("/public/media/" + NAME))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void storageFailureIs502() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new java.io.IOException("boom"));

        mockMvc.perform(get("/public/media/" + NAME))
                .andExpect(status().isBadGateway());
    }

    @Test
    void namesOutsideTheUuidJpgShapeNeverReachTheBucket() throws Exception {
        for (String bad : new String[] {
            "ticket-logos/1.png", "..%2Fsecret.jpg", "logo.png", "ABC.jpg",
            "0B6F1C2E-3D4A-4F5B-8C9D-1E2F3A4B5C6D.jpg", NAME + ".png"
        }) {
            mockMvc.perform(get("/public/media/" + bad)).andExpect(status().is4xxClientError());
        }
        verify(minioClient, never()).getObject(any(GetObjectArgs.class));
    }
}
