package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.enemy.Enemy;
import vajra.weapon.WeaponDef;

/** The loader falls back to coloured dots silently, so a weapon renamed (or a sprite dropped)
 *  would never surface on screen. This is the check that fails instead. */
class BulletImagesTest {
    @Test
    void everyBulletHasA16x16Sprite() throws Exception {
        File dir = bulletDir();
        assertNotNull(dir, "no assets/atala/bullets found from " + new File(".").getAbsolutePath());
        for (WeaponDef d : WeaponDef.ALL) {
            if (d.beam) continue;
            BufferedImage img = read(dir, d.name);
            assertNotNull(img, "no sprite for " + d.name);
            assertEquals(16, img.getWidth(), d.name + " width");
            assertEquals(16, img.getHeight(), d.name + " height");
        }
        assertNotNull(read(dir, "enemy"), "no sprite for enemy fire");
        for (WeaponDef d : Enemy.GUN) { // each kind's firing signature carries its own art
            if (d == null) continue;
            BufferedImage img = read(dir, d.name);
            assertNotNull(img, "no sprite for " + d.name);
            assertEquals(16, img.getWidth(), d.name + " width");
            assertEquals(16, img.getHeight(), d.name + " height");
        }
    }

    private static File bulletDir() {
        for (String p : new String[] { "assets/atala/bullets", "../assets/atala/bullets" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }

    private static BufferedImage read(File dir, String weaponName) throws Exception {
        File f = new File(dir, new File(BulletImages.artPath(weaponName)).getName());
        return f.isFile() ? ImageIO.read(f) : null;
    }
}
