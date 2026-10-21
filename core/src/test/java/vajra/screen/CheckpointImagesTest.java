package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The loader falls back to drawn placeholders silently, so a renamed or missing checkpoint
 *  crop would never surface on screen. This is the check that fails instead. */
class CheckpointImagesTest {
    @Test
    void threeCheckpointCropsExistAt32px() throws Exception {
        File dir = cpDir();
        assertNotNull(dir, "no assets/checkpoint found from " + new File(".").getAbsolutePath());
        for (String name : new String[] { "checkpoint_holder", "checkpoint_orb1", "checkpoint_orb2" }) {
            File f = new File(dir, name + ".png");
            assertTrue(f.isFile(), "missing " + name);
            BufferedImage img = ImageIO.read(f);
            assertNotNull(img, name + " unreadable");
            assertEquals(32, img.getWidth(), name + " width");
            assertEquals(32, img.getHeight(), name + " height");
        }
    }

    private static File cpDir() {
        for (String p : new String[] { "assets/checkpoint", "../assets/checkpoint" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }
}
