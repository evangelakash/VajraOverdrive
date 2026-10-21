package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The scripture popup phrases are pre-rendered Devanagari PNGs (libGDX FreeType cannot
 *  shape them): every key PopupArt knows must exist on disk at a usable size, or that
 *  popup silently falls back to its English line. */
class PopupArtTest {
    @Test
    void everyPopupPhraseExists() throws Exception {
        for (String key : PopupArt.KEYS) {
            File f = file(key + ".png");
            assertNotNull(f, "no assets/popup_art/" + key + ".png from "
                    + new File(".").getAbsolutePath());
            BufferedImage img = ImageIO.read(f);
            assertTrue(img.getWidth() > 40, key + " too narrow: " + img.getWidth());
            assertTrue(img.getHeight() > 30, key + " too short: " + img.getHeight());
        }
    }

    private static File file(String name) {
        for (String p : new String[] { "assets/popup_art", "../assets/popup_art" }) {
            File f = new File(p, name);
            if (f.isFile()) return f;
        }
        return null;
    }
}
