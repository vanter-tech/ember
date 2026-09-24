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
import javax.imageio.ImageIO;

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

    private TicketLogoRenderer() {}

    static BufferedImage decode(byte[] png) {
        if (png == null || png.length == 0) {
            return null;
        }
        try {
            return ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException e) {
            return null;
        }
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
