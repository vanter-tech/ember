package com.vanter.ember.printing.logo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Pure image work for the receipt logo: normalize an uploaded picture once, then turn it into
 * the 1-bit (black/white) bitmap a thermal printer actually prints, sized to the paper width.
 * Thermal heads have no grays, so tones are simulated with Floyd-Steinberg dithering.
 */
public final class TicketLogoProcessor {

    /** Printable dots per line at 203 dpi: 58 mm paper and 80 mm paper. */
    public static final int WIDTH_58MM_DOTS = 384;
    public static final int WIDTH_80MM_DOTS = 576;

    private static final int STORED_MAX_WIDTH = 1024;
    private static final int PRINT_MAX_HEIGHT = 300;
    private static final int THRESHOLD = 128;

    private TicketLogoProcessor() {}

    /** Decode any ImageIO-readable upload, flatten transparency onto white, cap the size, re-encode as PNG. */
    public static byte[] normalize(byte[] source) {
        BufferedImage decoded = decode(source);
        BufferedImage flat = flattenOnWhite(decoded);
        BufferedImage capped = scaleToFit(flat, STORED_MAX_WIDTH, STORED_MAX_WIDTH);
        return encodePng(capped);
    }

    /** 1-bit PNG no wider than {@code widthDots} (and no taller than a sane header), dithered. */
    public static byte[] toBitonalPng(byte[] normalizedPng, int widthDots) {
        BufferedImage scaled = scaleToFit(flattenOnWhite(decode(normalizedPng)), widthDots, PRINT_MAX_HEIGHT);
        return encodePng(dither(scaled));
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("Unsupported or corrupt image (use PNG, JPG or GIF)");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read the image: " + e.getMessage(), e);
        }
    }

    private static BufferedImage flattenOnWhite(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /** Scales down (never up) preserving the aspect ratio. */
    private static BufferedImage scaleToFit(BufferedImage src, int maxWidth, int maxHeight) {
        double ratio = Math.min(1.0, Math.min((double) maxWidth / src.getWidth(), (double) maxHeight / src.getHeight()));
        if (ratio >= 1.0) {
            return src;
        }
        int w = Math.max(1, (int) Math.round(src.getWidth() * ratio));
        int h = Math.max(1, (int) Math.round(src.getHeight() * ratio));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage dither(BufferedImage rgb) {
        int w = rgb.getWidth();
        int h = rgb.getHeight();
        float[][] gray = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int p = rgb.getRGB(x, y);
                gray[y][x] = 0.299f * ((p >> 16) & 0xFF) + 0.587f * ((p >> 8) & 0xFF) + 0.114f * (p & 0xFF);
            }
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float old = gray[y][x];
                float now = old < THRESHOLD ? 0f : 255f;
                out.setRGB(x, y, now == 0f ? 0x000000 : 0xFFFFFF);
                float err = old - now;
                if (x + 1 < w) gray[y][x + 1] += err * 7f / 16f;
                if (y + 1 < h) {
                    if (x > 0) gray[y + 1][x - 1] += err * 3f / 16f;
                    gray[y + 1][x] += err * 5f / 16f;
                    if (x + 1 < w) gray[y + 1][x + 1] += err * 1f / 16f;
                }
            }
        }
        return out;
    }

    private static byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode PNG", e);
        }
    }
}
