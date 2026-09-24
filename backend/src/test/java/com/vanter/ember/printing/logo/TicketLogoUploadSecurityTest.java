package com.vanter.ember.printing.logo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.vanter.ember.config.MinioProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

/** Hostile-upload cases for the ticket logo: nothing dangerous may reach storage or the printer. */
class TicketLogoUploadSecurityTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static BufferedImage image(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, w / 2 + 1, h);
        g.dispose();
        return img;
    }

    private static byte[] encode(BufferedImage img, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, format, out)) {
            throw new IllegalStateException("no writer for " + format);
        }
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteBuffer buf = ByteBuffer.allocate(12 + data.length);
        buf.putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue());
        return buf.array();
    }

    /** A PNG whose header claims {@code w x h} but whose pixel data is a handful of bytes. */
    private static byte[] pngClaiming(int w, int h) {
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[] {0, 0, 0, 0});
        deflater.finish();
        byte[] buf = new byte[64];
        int n = deflater.deflate(buf);
        byte[] idat = java.util.Arrays.copyOf(buf, n);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        out.writeBytes(chunk("IHDR", ihdr.array()));
        out.writeBytes(chunk("IDAT", idat));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    @Test
    void aTinyFileClaimingGigapixels_isRejectedBeforeAnyPixelIsDecoded() {
        byte[] bomb = pngClaiming(30_000, 30_000);
        assertThat(bomb.length).isLessThan(200);

        long start = System.nanoTime();
        assertThatThrownBy(() -> TicketLogoProcessor.normalize(bomb))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
        assertThat((System.nanoTime() - start) / 1_000_000).as("rejected from the header, no big allocation").isLessThan(2000);
    }

    @Test
    void anImageOverTheDimensionCap_isRejectedEvenIfItIsSmallInBytes() throws Exception {
        byte[] wide = encode(image(5000, 10), "png");

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(wide))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void anExecutableRenamedToPng_isRejected() {
        byte[] exe = concat("MZ".getBytes(), new byte[] {(byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF});

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(exe))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void htmlOrScriptContent_isRejected() {
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(html)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void svgIsRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(svg)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bmpAndTiff_areRejectedEvenThoughImageIoCanReadThem() throws Exception {
        for (String format : new String[] {"bmp", "tiff"}) {
            byte[] bytes;
            try {
                bytes = encode(image(20, 20), format);
            } catch (IllegalStateException noWriter) {
                continue;
            }
            assertThatThrownBy(() -> TicketLogoProcessor.normalize(bytes))
                    .as(format)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported");
        }
    }

    @Test
    void phpAppendedAfterAValidPng_doesNotSurviveReEncoding() throws Exception {
        byte[] polyglot = concat(encode(image(40, 40), "png"), "<?php system($_GET['c']); ?>".getBytes());

        byte[] stored = TicketLogoProcessor.normalize(polyglot);

        assertThat(contains(stored, "<?php")).isFalse();
        assertThat(java.util.Arrays.copyOf(stored, 8)).containsExactly(PNG_SIGNATURE);
    }

    @Test
    void aScriptInAPngTextChunk_doesNotSurviveReEncoding() throws Exception {
        byte[] plain = encode(image(40, 40), "png");
        byte[] beforeIend = java.util.Arrays.copyOf(plain, plain.length - 12);
        byte[] text = chunk("tEXt", "Comment\0<script>alert(1)</script>".getBytes(StandardCharsets.ISO_8859_1));
        byte[] withText = concat(beforeIend, text, java.util.Arrays.copyOfRange(plain, plain.length - 12, plain.length));
        assertThat(contains(withText, "<script>")).as("test file carries the payload").isTrue();

        byte[] stored = TicketLogoProcessor.normalize(withText);

        assertThat(contains(stored, "<script>")).isFalse();
    }

    @Test
    void aJpegWithAnExifCommentPayload_isStrippedToo() throws Exception {
        byte[] jpeg = encode(image(40, 40), "jpeg");
        // COM segment (FF FE) with a payload, inserted right after the SOI marker.
        byte[] payload = "<script>evil()</script>".getBytes(StandardCharsets.ISO_8859_1);
        byte[] com = concat(new byte[] {(byte) 0xFF, (byte) 0xFE, 0, (byte) (payload.length + 2)}, payload);
        byte[] tampered = concat(java.util.Arrays.copyOf(jpeg, 2), com, java.util.Arrays.copyOfRange(jpeg, 2, jpeg.length));

        byte[] stored = TicketLogoProcessor.normalize(tampered);

        assertThat(contains(stored, "evil()")).isFalse();
        assertThat(java.util.Arrays.copyOf(stored, 8)).containsExactly(PNG_SIGNATURE);
    }

    @Test
    void aValidSignatureWithGarbageBody_failsGenericallyNotWithDecoderInternals() {
        byte[] garbage = concat(PNG_SIGNATURE, new byte[200]);

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(garbage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Could not read the image");
    }

    @Test
    void aTruncatedPng_isRejected() throws Exception {
        byte[] png = encode(image(200, 200), "png");

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(java.util.Arrays.copyOf(png, png.length / 2)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legitimatePngJpegAndGif_areAccepted_andAlwaysComeOutAsPng() throws Exception {
        for (String format : new String[] {"png", "jpeg", "gif"}) {
            byte[] stored = TicketLogoProcessor.normalize(encode(image(120, 60), format));

            assertThat(java.util.Arrays.copyOf(stored, 8)).as(format).containsExactly(PNG_SIGNATURE);
        }
    }

    @Test
    void aGifWithTooManyFrames_isRejected() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            writer.prepareWriteSequence(null);
            for (int i = 0; i < TicketLogoProcessor.MAX_GIF_FRAMES + 5; i++) {
                writer.writeToSequence(new IIOImage(image(8, 8), null, null), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        assertThatThrownBy(() -> TicketLogoProcessor.normalize(out.toByteArray()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("frames");
    }

    @Test
    void theServiceRefusesADeclaredNonImageTypeAndStoresNothing() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        TicketLogoService service = new TicketLogoService(minio, props);
        byte[] realPng = encode(image(30, 30), "png");

        for (String declared : new String[] {"text/html", "image/svg+xml", "application/x-msdownload"}) {
            assertThatThrownBy(() -> service.store(
                            UUID.randomUUID(), new MockMultipartFile("file", "logo.png", declared, realPng)))
                    .as(declared)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(minio, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void theServiceStoresOnlyTheReEncodedPng_neverTheUploadedBytes() throws Exception {
        MinioClient minio = mock(MinioClient.class);
        MinioProperties props = new MinioProperties();
        props.setBucket("bucket");
        TicketLogoService service = new TicketLogoService(minio, props);
        byte[] polyglot = concat(encode(image(30, 30), "png"), "<?php evil(); ?>".getBytes());

        service.store(UUID.randomUUID(), new MockMultipartFile("file", "../../etc/passwd.png", "image/png", polyglot));

        org.mockito.ArgumentCaptor<PutObjectArgs> captor = org.mockito.ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(captor.capture());
        // The object name never derives from the (hostile) file name.
        assertThat(captor.getValue().object()).startsWith("ticket-logos/").endsWith(".png").doesNotContain("passwd");
        assertThat(captor.getValue().contentType()).isEqualTo("image/png");
    }
}
