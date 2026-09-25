package com.vanter.ember.printing.logo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vanter.ember.config.MinioProperties;
import com.vanter.ember.settings.model.SettingsPayload;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class TicketLogoServiceTest {

    private final UUID tenant = UUID.randomUUID();
    private MinioClient minio;
    private TicketLogoService service;

    @BeforeEach
    void setUp() {
        minio = mock(MinioClient.class);
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        service = new TicketLogoService(minio, props);
    }

    @Test
    void objectKey_isDerivedFromTheTenantOnly() {
        assertThat(TicketLogoService.objectKey(tenant)).isEqualTo("ticket-logos/" + tenant + ".png");
    }

    @Test
    void store_rejectsAnEmptyFile() {
        assertThatThrownBy(() -> service.store(tenant, new MockMultipartFile("file", new byte[0])))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_rejectsAFileOver2Mb() {
        byte[] big = new byte[2 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> service.store(tenant, new MockMultipartFile("file", "big.png", "image/png", big)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2MB");
    }

    @Test
    void store_rejectsANonImage() {
        assertThatThrownBy(() -> service.store(
                        tenant, new MockMultipartFile("file", "x.png", "image/png", "nope".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void store_writesTheNormalizedPngUnderTheTenantKey() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);

        service.store(tenant, new MockMultipartFile("file", "logo.png", "image/png", out.toByteArray()));

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(captor.capture());
        assertThat(captor.getValue().object()).isEqualTo("ticket-logos/" + tenant + ".png");
        assertThat(captor.getValue().bucket()).isEqualTo("bucket");
    }

    @Test
    void exists_isFalseWhenStorageFails() throws Exception {
        when(minio.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("no such key"));

        assertThat(service.exists(tenant)).isFalse();
    }

    @Test
    void loadBitonal_isEmptyWhenStorageFails() throws Exception {
        when(minio.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("boom"));

        assertThat(service.loadBitonal(tenant, SettingsPayload.PaperWidth.MM_80)).isEmpty();
    }
}
