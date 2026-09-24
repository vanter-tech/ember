package com.vanter.ember.printing.logo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Pure image work for the receipt logo: validate and normalize an uploaded picture once, then turn
 * it into the 1-bit (black/white) bitmap a thermal printer actually prints, sized to the paper.
 * Thermal heads have no grays, so tones are simulated with Floyd-Steinberg dithering.
 *
 * <p>The upload is untrusted input, so it is never stored as received: the file signature must be
 * PNG, JPEG or GIF (not merely "something ImageIO can read"), the declared dimensions are checked
 * <em>before</em> any pixel is decoded (a tiny file can claim gigapixels), and the pixels are
 * re-encoded as a fresh PNG, which discards metadata, comments and any bytes appended after the
 * image. Every failure surfaces as {@link IllegalArgumentException} with a generic message.
 */
public final class TicketLogoProcessor {

    /** Printable dots per line at 203 dpi: 58 mm paper and 80 mm paper. */
    public static final int WIDTH_58MM_DOTS = 384;
    public static final int WIDTH_80MM_DOTS = 576;

    /** Largest accepted upload, in either dimension and in total pixels (decode memory ~ 4 bytes each). */
    static final int MAX_DIMENSION = 4096;
    static final long MAX_PIXELS = 12_000_000L;
    /** More GIF frames than any sane logo needs; only the first one is ever used. */
    static final int MAX_GIF_FRAMES = 100;
    /** Cap on the stored (re-encoded) PNG. */
    static final int MAX_STORED_BYTES = 1024 * 1024;

    private static final int STORED_MAX_WIDTH = 1024;
    /** The printed logo takes at most this share of the paper width, and stays a compact header. */
    private static final double PRINT_WIDTH_FRACTION = 0.5;
    private static final int PRINT_MAX_HEIGHT = 120;
    private static final int THRESHOLD = 128;

    private static final Set<String> ALLOWED_FORMATS = Set.of("png", "jpeg", "gif");

    static {
        // Never spool a decoded image to a temp file on disk.
        ImageIO.setUseCache(false);
    }

    private TicketLogoProcessor() {}

    /** Validate any PNG/JPEG/GIF upload, flatten transparency onto white, cap the size, re-encode as PNG. */
    public static byte[] normalize(byte[] source) {
        BufferedImage decoded = decode(source);
        BufferedImage flat = flattenOnWhite(decoded);
        BufferedImage capped = scaleToFit(flat, STORED_MAX_WIDTH, STORED_MAX_WIDTH);
        byte[] png = encodePng(capped);
        if (png.length > MAX_STORED_BYTES) {
            throw new IllegalArgumentException("The image is too complex; use a simpler or smaller logo");
        }
        return png;
    }

    /**
     * 1-bit PNG for a paper {@code paperWidthDots} wide: at most half that width and 120 dots
     * tall, aspect preserved, never upscaled, dithered. The agent centers it on the line.
     */
    public static byte[] toBitonalPng(byte[] normalizedPng, int paperWidthDots) {
        int maxWidth = (int) Math.round(paperWidthDots * PRINT_WIDTH_FRACTION);
        BufferedImage scaled = scaleToFit(flattenOnWhite(decode(normalizedPng)), maxWidth, PRINT_MAX_HEIGHT);
        return encodePng(dither(scaled));
    }

    /** {@code png}, {@code jpeg} or {@code gif} by file signature; null for anything else. */
    static String detectFormat(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "gif";
        }
        return null;
    }

    private static BufferedImage decode(byte[] bytes) {
        String format = detectFormat(bytes);
        if (format == null) {
            throw new IllegalArgumentException("Unsupported image type (use PNG, JPG or GIF)");
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = readerFor(in, format);
            try {
                reader.setInput(in, false, true);
                // Dimensions come from the header alone: nothing is allocated for pixels yet.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) {
                    throw new IllegalArgumentException("The image is too large (max "
                            + MAX_DIMENSION + "x" + MAX_DIMENSION + " pixels)");
                }
                if ("gif".equals(format) && reader.getNumImages(true) > MAX_GIF_FRAMES) {
                    throw new IllegalArgumentException("Animated images with that many frames are not supported");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IllegalArgumentException("Could not read the image");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            // Corrupt or hostile data: report generically, never echo decoder internals.
            throw new IllegalArgumentException("Could not read the image");
        }
    }

    private static ImageReader readerFor(ImageInputStream in, String format) {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            String name;
            try {
                name = reader.getFormatName().toLowerCase(Locale.ROOT);
            } catch (IOException e) {
                reader.dispose();
                continue;
            }
            if (ALLOWED_FORMATS.contains(name) && name.equals(format)) {
                return reader;
            }
            reader.dispose();
        }
        throw new IllegalArgumentException("Unsupported image type (use PNG, JPG or GIF)");
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
