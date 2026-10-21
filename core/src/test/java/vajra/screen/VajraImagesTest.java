package vajra.screen;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The Vajrayudha is one image cut into seven loka bands. A missing file would silently fall
 *  back to placeholder gold, and a bad split would drop or overlap a band — both must fail. */
class VajraImagesTest {
    @Test
    void theSevenBandsSplitWithoutGapsOrOverlaps() {
        int h = 32; // vj.png's height
        int sum = 0;
        int prevTop = -1, prevEnd = 0;
        for (int i = 0; i < VajraImages.BANDS; i++) {
            int top = VajraImages.bandTop(i, h);
            int bh = VajraImages.bandH(i, h);
            assertTrue(top > prevTop, "band " + i + " starts below the previous one");
            assertEquals(prevEnd, top, "band " + i + " starts where the last ended: no gap");
            assertTrue(bh >= 1, "band " + i + " has at least one row");
            sum += bh;
            prevTop = top;
            prevEnd = top + bh;
        }
        assertEquals(h, sum, "the seven heights sum back to the image height");
    }

    @Test
    void theVajrayudhaImageIsOnDisk() throws Exception {
        File assets = assetsDir();
        assertNotNull(assets, "no assets folder found from " + new File(".").getAbsolutePath());
        File f = new File(assets, "vajrayudha/vj.png");
        assertTrue(f.isFile(), "no Vajrayudha image at assets/vajrayudha/vj.png");
        BufferedImage img = ImageIO.read(f);
        assertTrue(img.getHeight() >= VajraImages.BANDS, "tall enough to split into seven bands");
    }

    private static File assetsDir() {
        for (String p : new String[] { "assets", "../assets" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }
}
