package com.vanter.emberagent;

import com.github.anastaciocintra.escpos.EscPos;
import com.github.anastaciocintra.escpos.EscPosConst;
import com.github.anastaciocintra.escpos.image.BitonalThreshold;
import com.github.anastaciocintra.escpos.image.CoffeeImageImpl;
import com.github.anastaciocintra.escpos.image.EscPosImage;
import com.github.anastaciocintra.escpos.image.RasterBitImageWrapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Prints the restaurant's receipt logo. The backend already sends a 1-bit bitmap sized to the
 * paper width, so this only has to emit it as an ESC/POS raster image (centered) or, for the
 * Windows-driver mode, hand back a {@link BufferedImage} to draw.
 *
 * <p>A logo problem must never cost a customer their ticket: an undecodable image is skipped
 * (returns {@code false}) rather than thrown.
 */
final class TicketLogoRenderer {

    private static final int ESC_A_LEFT = 0;

    /** The backend sends a small 1-bit PNG (<= half the paper width); anything bigger is not from it. */
    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_DIMENSION = 1200;

    static {
        // Never spool a decoded image to a temp file on disk.
        ImageIO.setUseCache(false);
    }

    private TicketLogoRenderer() {}

    static BufferedImage decode(byte[] png) {
        if (png == null || png.length == 0 || png.length > MAX_BYTES || !hasPngSignature(png)) {
            return null;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(png))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Header first: never allocate pixels for an image that claims to be huge.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static boolean hasPngSignature(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }

    /**
     * Writes the logo centered at the current position, then restores left alignment (the
     * raster wrapper emits {@code ESC a 1}, which would otherwise center the whole ticket text)
     * and leaves one blank line.
     *
     * @return whether a logo was actually written
     */
    static boolean write(EscPos escPos, byte[] png) throws IOException {
        BufferedImage image = decode(png);
        if (image == null) {
            return false;
        }
        RasterBitImageWrapper wrapper = new RasterBitImageWrapper();
        wrapper.setJustification(EscPosConst.Justification.Center);
        escPos.write(wrapper, new EscPosImage(new CoffeeImageImpl(image), new BitonalThreshold()));
        escPos.write(EscPosConst.ESC).write('a').write(ESC_A_LEFT);
        escPos.feed(1);
        return true;
    }
}
