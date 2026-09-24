package com.vanter.ember.printing.logo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TicketLogoProcessorTest {

    private static byte[] png(int w, int h, Color fill, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(fill);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void normalize_flattensTransparencyOntoWhite() throws Exception {
        byte[] transparent = png(20, 20, new Color(0, 0, 0, 0), true);

        BufferedImage out = read(TicketLogoProcessor.normalize(transparent));

        assertThat(out.getRGB(10, 10) & 0xFFFFFF).isEqualTo(0xFFFFFF);
    }

    @Test
    void normalize_capsHugeImages() throws Exception {
        BufferedImage out = read(TicketLogoProcessor.normalize(png(3000, 1500, Color.BLACK, false)));

        assertThat(out.getWidth()).isLessThanOrEqualTo(1024);
    }

    @Test
    void toBitonalPng_scalesDownToPaperWidthKeepingAspectRatio() throws Exception {
        byte[] wide = TicketLogoProcessor.normalize(png(1000, 250, Color.BLACK, false));

        BufferedImage out = read(TicketLogoProcessor.toBitonalPng(wide, TicketLogoProcessor.WIDTH_58MM_DOTS));

        assertThat(out.getWidth()).isEqualTo(384);
        assertThat(out.getHeight()).isEqualTo(96);
    }

    @Test
    void toBitonalPng_neverUpscalesASmallLogo() throws Exception {
        byte[] small = png(100, 50, Color.BLACK, false);

        BufferedImage out = read(TicketLogoProcessor.toBitonalPng(small, TicketLogoProcessor.WIDTH_80MM_DOTS));

        assertThat(out.getWidth()).isEqualTo(100);
    }

    @Test
    void toBitonalPng_capsTheHeightOfATallLogo() throws Exception {
        BufferedImage out = read(TicketLogoProcessor.toBitonalPng(png(200, 900, Color.BLACK, false), 576));

        assertThat(out.getHeight()).isLessThanOrEqualTo(300);
    }

    @Test
    void toBitonalPng_outputHasOnlyBlackAndWhite_andAMidGrayBecomesAMix() throws Exception {
        BufferedImage out = read(TicketLogoProcessor.toBitonalPng(png(64, 64, new Color(128, 128, 128), false), 576));

        int black = 0;
        int white = 0;
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int rgb = out.getRGB(x, y) & 0xFFFFFF;
                if (rgb == 0x000000) {
                    black++;
                } else if (rgb == 0xFFFFFF) {
                    white++;
                } else {
                    throw new AssertionError("non bitonal pixel " + Integer.toHexString(rgb));
                }
            }
        }
        assertThat(black).isPositive();
        assertThat(white).isPositive();
    }

    @Test
    void normalize_rejectsSomethingThatIsNotAnImage() {
        assertThatThrownBy(() -> TicketLogoProcessor.normalize("not an image".getBytes()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
