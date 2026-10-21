package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** FxImages falls back silently to drawn shapes when the pack crops are missing, so a renamed
 *  file would never surface on screen. This is the check that fails instead. */
class FxImagesTest {
    @Test
    void beamAndRingAreCroppedAndWhitened() throws Exception {
        File dir = fxDir();
        assertNotNull(dir, "no assets/atala/fx found from " + new File(".").getAbsolutePath());

        BufferedImage beam = read(dir, "beam");
        assertNotNull(beam, "no beam sprite");
        assertEquals(16, beam.getWidth(), "beam width");
        assertEquals(8, beam.getHeight(), "beam height");
        assertTrue(peakAlpha(beam) == 255, "beam core should be fully opaque");

        BufferedImage ring = read(dir, "ring");
        assertNotNull(ring, "no ring sprite");
        assertEquals(16, ring.getWidth(), "ring width");
        assertEquals(16, ring.getHeight(), "ring height");
        assertEquals(255, peakAlpha(ring), "ring core should be fully opaque");

        // whitened for tinting: every visible pixel is white, brightness lives in alpha
        assertTrue(isWhite(ring), "ring must be white so a weapon colour can tint it");
        assertTrue(isWhite(beam), "beam must be white so a weapon colour can tint it");
    }

    @Test
    void thrustComesInFourLevelsOfFourFrames() throws Exception {
        File dir = fxDir();
        assertNotNull(dir, "no assets/atala/fx found from " + new File(".").getAbsolutePath());
        for (int lvl = 0; lvl < 4; lvl++) {
            for (int frame = 0; frame < 4; frame++) {
                String name = "thrust-l" + lvl + "-f" + frame;
                BufferedImage img = read(dir, name);
                assertNotNull(img, "no thrust sprite for " + name);
                assertEquals(255, peakAlpha(img), name + " core should be fully opaque");
                assertTrue(isWhite(img), name + " must be white so the ship colour can tint it");
                assertTrue(trimmed(img), name + " must be trimmed to its ink or it sits off-centre");
            }
        }
        // the pack rows grow with power: level 3 must carry more ink than level 0
        assertTrue(ink(dir, 3) > ink(dir, 0), "plume levels must grow with thrust");
    }

    @Test
    void hullSmokeAndTorchFlameSheetsAreSized() throws Exception {
        File smokeF = asset("effects/smoke-low-hull/low_hull.png");
        assertNotNull(smokeF, "no hull smoke sheet");
        BufferedImage smoke = ImageIO.read(smokeF);
        assertEquals(32, smoke.getWidth(), "smoke sheet width — 2x2 grid of 16px frames");
        assertEquals(32, smoke.getHeight(), "smoke sheet height");
        for (int qy = 0; qy < 2; qy++) // all four frames carry ink
            for (int qx = 0; qx < 2; qx++) {
                int n = 0;
                for (int y = qy * 16; y < qy * 16 + 16; y++)
                    for (int x = qx * 16; x < qx * 16 + 16; x++)
                        if (alpha(smoke, x, y) > 0) n++;
                assertTrue(n > 0, "frame " + (qy * 2 + qx) + " of the smoke sheet is empty");
            }

        File flameF = asset("effects/small-flame/pixilart-sprite.png");
        assertNotNull(flameF, "no torch flame sheet");
        BufferedImage flame = ImageIO.read(flameF);
        assertEquals(48, flame.getWidth(), "flame sheet width — 3 frames of 16px");
        assertEquals(16, flame.getHeight(), "flame sheet height");
        for (int f = 0; f < 3; f++) {
            int n = 0;
            for (int y = 0; y < 16; y++)
                for (int x = f * 16; x < f * 16 + 16; x++)
                    if (alpha(flame, x, y) > 0) n++;
            assertTrue(n > 0, "flame frame " + f + " is empty");
        }
    }

    @Test
    void plantSheetsAreFourStripsOf16pxFrames() throws Exception {
        int[] frames = { 3, 3, 12, 8 }; // plant_1..4, read off the sheets
        for (int i = 0; i < frames.length; i++) {
            File f = asset("atala/plants/plant_" + (i + 1) + ".png");
            assertNotNull(f, "no plant_" + (i + 1) + " sheet");
            BufferedImage img = ImageIO.read(f);
            assertEquals(16, img.getHeight(), "plant_" + (i + 1) + " height");
            assertEquals(frames[i] * 16, img.getWidth(), "plant_" + (i + 1) + " strip width");
            for (int fr = 0; fr < frames[i]; fr++) {
                int n = 0;
                for (int y = 0; y < 16; y++)
                    for (int x = fr * 16; x < fr * 16 + 16; x++)
                        if (alpha(img, x, y) > 0) n++;
                assertTrue(n > 0, "plant_" + (i + 1) + " frame " + fr + " is empty");
            }
        }
    }

    @Test
    void plantSwayPingPongsOverAnySheetLength() {
        int[] three = { 0, 1, 2, 1, 0 }; // the old fixed cycle, one step per half second
        for (int i = 0; i < three.length; i++)
            assertEquals(three[i], FxImages.plantFrame(3, i * 0.5f, 0f), "3-frame step " + i);
        int[] twelve = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1 };
        for (int i = 0; i < twelve.length; i++) // whole 2 s cycle: ramp up, ramp back
            assertEquals(twelve[i], FxImages.plantFrame(12, i / 11f + 0.01f, 0f), "12-frame step " + i);
    }

    private static File asset(String rel) {
        for (String p : new String[] { "assets/" + rel, "../assets/" + rel }) {
            File f = new File(p);
            if (f.isFile()) return f;
        }
        return null;
    }

    private static int ink(File dir, int lvl) throws Exception {
        int n = 0;
        for (int frame = 0; frame < 4; frame++) {
            BufferedImage img = read(dir, "thrust-l" + lvl + "-f" + frame);
            for (int y = 0; y < img.getHeight(); y++)
                for (int x = 0; x < img.getWidth(); x++)
                    if (((img.getRGB(x, y) >>> 24) & 0xFF) > 0) n++;
        }
        return n;
    }

    private static File fxDir() {
        for (String p : new String[] { "assets/atala/fx", "../assets/atala/fx" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }

    private static BufferedImage read(File dir, String name) throws Exception {
        File f = new File(dir, new File(FxImages.artPath(name)).getName());
        return f.isFile() ? ImageIO.read(f) : null;
    }

    private static int peakAlpha(BufferedImage img) {
        int peak = 0;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++)
                peak = Math.max(peak, (img.getRGB(x, y) >>> 24) & 0xFF);
        return peak;
    }

    private static boolean isWhite(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y), a = (rgb >>> 24) & 0xFF;
                if (a == 0) continue;
                if (((rgb >> 16) & 0xFF) != 255 || ((rgb >> 8) & 0xFF) != 255 || (rgb & 0xFF) != 255)
                    return false;
            }
        return true;
    }

    /** No transparent border: every edge row/column carries ink, so the sprite centres exactly. */
    private static boolean trimmed(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        boolean top = false, bottom = false, left = false, right = false;
        for (int x = 0; x < w; x++) {
            top |= alpha(img, x, 0) > 0;
            bottom |= alpha(img, x, h - 1) > 0;
        }
        for (int y = 0; y < h; y++) {
            left |= alpha(img, 0, y) > 0;
            right |= alpha(img, w - 1, y) > 0;
        }
        return top && bottom && left && right;
    }

    private static int alpha(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) >>> 24) & 0xFF;
    }
}
