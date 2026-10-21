package vajra.screen;

import vajra.ship.Ship;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.ship.ShipDef;

/** Ship sprites silently fall back to the polygon hull, so a missing folder or a renamed
 *  variation would never surface on screen. This is the check that fails instead. */
class ShipArtTest {
    @Test
    void everyShipHasThree32x32Variations() throws Exception {
        File assets = assetsDir();
        assertNotNull(assets, "no assets folder found from " + new File(".").getAbsolutePath());
        for (ShipDef d : ShipDef.ALL) {
            for (int v = 1; v <= 3; v++) {
                File f = new File(assets, ShipImages.artPath(d.id, v));
                assertTrue(f.isFile(), "no sprite for " + d.name + " variation " + v);
                BufferedImage img = ImageIO.read(f);
                assertEquals(32, img.getWidth(), d.name + " v" + v + " width");
                assertEquals(32, img.getHeight(), d.name + " v" + v + " height");
            }
        }
    }

    /** Variation tracks the highest bought tier, so the ladder must be 1 -> 2 -> 3. */
    @Test
    void variationFollowsTheBoughtTiers() {
        vajra.world.World w = new vajra.world.World();
        w.hangar.coins = 9999; // test purse: the yard ships with 0
        vajra.ship.Ship s = w.ship;
        assertEquals(1, ShipRenderer.variation(s), "base tier ships with the hull");
        w.hangar.buy(s.def, 1);
        assertEquals(2, ShipRenderer.variation(s));
        w.hangar.shards[0] = vajra.ship.Hangar.SHARD_COST; // x.3 wants shards on top of coins
        w.hangar.buy(s.def, 2);
        assertEquals(3, ShipRenderer.variation(s));
    }

    /** A hold has to climb through all four pack levels: tap, small, large, max. Boost runs the
     *  same ramp — it changes hue and length instead of pinning the level. */
    @Test
    void plumeLevelsRampWithHoldTime() {
        vajra.world.World w = new vajra.world.World();
        vajra.ship.Ship s = w.ship;
        assertEquals(0, ShipRenderer.level(s), "fresh tap idles at level 0");
        s.thrustHold = 0.15f;
        assertEquals(1, ShipRenderer.level(s), "held a moment = level 1");
        s.thrustHold = 0.4f;
        assertEquals(2, ShipRenderer.level(s), "held longer = level 2");
        s.thrustHold = 0.9f;
        assertEquals(3, ShipRenderer.level(s), "held long = max thrust");
        s.boosting = true;
        assertEquals(3, ShipRenderer.level(s), "boost at full hold keeps the max level");
        s.thrustHold = 0f;
        assertEquals(0, ShipRenderer.level(s), "boost uses the same ramp, no pin to max");
        assertEquals(2f, ShipRenderer.stretch(s), "and the plume doubles in length");
        s.boosting = false;
        assertEquals(1f, ShipRenderer.stretch(s), "stowed when not boosting");
    }

    private static File assetsDir() {
        for (String p : new String[] { "assets", "../assets" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }
}
