package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.Test;

/** The agent decodes whatever the backend serves; it must not trust it blindly. */
class TicketLogoHardeningTest {

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        ByteBuffer buf = ByteBuffer.allocate(12 + data.length);
        buf.putInt(data.length).put(typeBytes).put(data).putInt((int) crc.getValue());
        return buf.array();
    }

    /** A PNG whose header claims w x h with only a few bytes of pixel data. */
    private static byte[] pngClaiming(int w, int h) {
        ByteBuffer ihdr = ByteBuffer.allocate(13);
        ihdr.putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[] {0, 0, 0, 0});
        deflater.finish();
        byte[] buf = new byte[64];
        byte[] idat = Arrays.copyOf(buf, deflater.deflate(buf));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        out.writeBytes(chunk("IHDR", ihdr.array()));
        out.writeBytes(chunk("IDAT", idat));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    @Test
    void decode_aTinyPngClaimingGigapixels_isRefusedBeforeAllocatingPixels() {
        assertNull(TicketLogoRenderer.decode(pngClaiming(30_000, 30_000)));
    }

    @Test
    void decode_aNormalLogo_stillWorks() throws Exception {
        assertNotNull(TicketLogoRenderer.decode(TicketLogoRendererTest.logoPng()));
    }

    @Test
    void decode_nonPngBytes_areRefused() {
        assertNull(TicketLogoRenderer.decode("<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)));
        assertNull(TicketLogoRenderer.decode(new byte[] {'B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
    }

    @Test
    void decode_anOversizedPayload_isRefused() {
        byte[] huge = new byte[TicketLogoRenderer.MAX_BYTES + 1];
        System.arraycopy(PNG_SIGNATURE, 0, huge, 0, PNG_SIGNATURE.length);

        assertNull(TicketLogoRenderer.decode(huge));
    }

    @Test
    void client_aBodyOverTheLimit_isIgnoredAndNotCached() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            byte[] tooBig = new byte[TicketLogoClient.MAX_BYTES + 10];
            server.enqueue(new MockResponse.Builder().code(200).body(new Buffer().write(tooBig))
                    .addHeader("ETag", "\"big\"").build());
            server.enqueue(new MockResponse.Builder().code(500).build());
            String base = server.url("/").toString();
            base = base.substring(0, base.length() - 1);
            TicketLogoClient client = new TicketLogoClient();

            assertTrue(client.fetch(base, "jwt").isEmpty());
            // nothing was cached: a later failure must not resurface the oversized body
            assertTrue(client.fetch(base, "jwt").isEmpty());
        }
    }

    @Test
    void write_withAnImageTheRendererRefuses_printsTheTicketWithoutIt() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        boolean written;
        try (com.github.anastaciocintra.escpos.EscPos escPos = new com.github.anastaciocintra.escpos.EscPos(buffer)) {
            written = TicketLogoRenderer.write(escPos, pngClaiming(30_000, 30_000));
        }

        org.junit.jupiter.api.Assertions.assertFalse(written);
    }
}
