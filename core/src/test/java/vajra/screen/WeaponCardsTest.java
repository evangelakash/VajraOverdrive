package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.weapon.WeaponDef;

/** The loader falls back to dim discs silently, so a weapon renamed (or a card dropped) would
 *  never surface on screen. This is the check that fails instead. */
class WeaponCardsTest {
    @Test
    void everyWeaponHasAFaceCard() throws Exception {
        File dir = cardDir();
        assertNotNull(dir, "no assets/weapon_display_images found from " + new File(".").getAbsolutePath());
        for (WeaponDef d : WeaponDef.ALL) {
            File f = new File(dir, WeaponCards.fileKey(d.name) + ".png");
            assertTrue(f.isFile(), "no face card for " + d.name + " (" + WeaponCards.fileKey(d.name) + ".png)");
            BufferedImage img = ImageIO.read(f);
            assertEquals(16, img.getWidth(), d.name + " width");
            assertEquals(16, img.getHeight(), d.name + " height");
        }
    }

    private static File cardDir() {
        for (String p : new String[] { "assets/weapon_display_images", "../assets/weapon_display_images" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }
}
