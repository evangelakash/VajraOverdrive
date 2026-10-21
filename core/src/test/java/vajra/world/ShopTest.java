package vajra.world;

import vajra.ship.ShipControls;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Shops: one hanging sign per Atala band 1-4 (none in Bhu, none in the boss band), alternating
 *  right/left of centre deeper than the entrance, the undocked sign + dock-sequence art is on
 *  disk, and flying into the marked bay runs docking to completion — leaving mid-way cancels,
 *  closing parks the bay as SITTING until you fly out. */
class ShopTest {
    private static final ShipControls NONE = new ShipControls() {
        public float thrustX() { return 0; }
        public float thrustY() { return 0; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return false; }
        public boolean dash() { return false; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    };

    /** Park the ship in shop i's marked bay (no stray contact knocks mid-test). */
    private static World inBay(int i) {
        World w = new World();
        w.enemies.clear();
        float[] s = w.shops[i];
        w.ship.x = s[0];
        w.ship.y = s[1] - 44;
        return w;
    }

    @Test
    void eachRegionGetsOneOnOpenGround() {
        World w = new World();
        assertEquals(4, w.shops.length, "bands 1-4 only: no Bhu shop, no boss-band shop");
        for (int i = 0; i < w.shops.length; i++) {
            float x = w.shops[i][0], y = w.shops[i][1];
            int r = i + 1;
            assertTrue(y > w.regionStart[r] && y < w.regionEnd[r], "stays in its Atala band");
            assertTrue(y < w.checkpoints[i].y, "shop near the entrance, checkpoint deeper");
            assertEquals(i % 2 == 0, x > w.width / 2, "alternates right, left, right, left");
            assertFalse(w.blockedPx(x, y), "not in rock at " + x + "," + y);
            assertFalse(w.blockedPx(x + 40, y), "room around it");
            assertFalse(w.blockedPx(x - 40, y));
            assertFalse(w.blockedPx(x, y + 40));
            assertFalse(w.blockedPx(x, y - 40));
        }
    }

    @Test
    void dockingRunsToCompletionInTheBay() {
        World w = new World();
        float[] s = w.shops[0];
        assertFalse(w.inDockBox(0, w.ship.x, w.ship.y), "the spawn is not a dock");

        w = inBay(0);
        s = w.shops[0];
        w.update(0.1f, NONE);
        assertEquals(World.DOCKING, w.shopState[0], "entering the bay starts the sequence");
        assertEquals(0f, w.shopTimer[0], 1e-6, "timer reset on entry");

        for (int i = 0; i < 5; i++) w.update(0.1f, NONE); // 0.5 s of the 0.6 s sequence
        assertEquals(World.DOCKING, w.shopState[0], "still animating at 0.5s");
        assertFalse(w.shopReady(), "not ready before the sequence ends");

        for (int i = 0; i < 3; i++) w.update(0.1f, NONE); // past DOCK_TIME
        assertEquals(World.DOCKED, w.shopState[0], "bay full: docked");
        assertTrue(w.shopReady(), "the screen opens on this");
    }

    @Test
    void leavingCancelsAndClosingParks() {
        World w = inBay(0);
        float[] s = w.shops[0];
        w.update(0.1f, NONE);
        assertEquals(World.DOCKING, w.shopState[0], "sequence started");

        w.ship.y = s[1] - 200; // fly out before the sequence ends
        w.update(0.1f, NONE);
        assertEquals(World.UNDOCKED, w.shopState[0], "leaving mid-sequence cancels it");

        w.ship.x = s[0];
        w.ship.y = s[1] - 44;
        for (int i = 0; i < 8; i++) w.update(0.1f, NONE); // dock for real
        assertEquals(World.DOCKED, w.shopState[0], "docked again");

        w.closeDocked(); // the player closed the shop
        assertEquals(World.SITTING, w.shopState[0], "closing parks the bay");
        assertFalse(w.shopReady(), "no reopening while parked");

        w.update(0.1f, NONE); // still sitting in the bay
        assertEquals(World.SITTING, w.shopState[0], "stays parked inside the bay");

        w.ship.y = s[1] - 200;
        w.update(0.1f, NONE);
        assertEquals(World.UNDOCKED, w.shopState[0], "flying out resets for the next dock");
    }

    @Test
    void hullMustFitFullyInsideTheBay() {
        World w = inBay(0);
        float[] s = w.shops[0];
        w.ship.y = s[1] - 58; // centre still inside the bay square, hull hangs out the open floor
        w.update(0.1f, NONE);
        assertEquals(World.UNDOCKED, w.shopState[0], "a hull sticking out is not a dock");
        assertFalse(w.shopReady(), "and nothing opens");

        w.ship.y = s[1] - 44; // slide the rest of the way in
        w.update(0.1f, NONE);
        assertEquals(World.DOCKING, w.shopState[0], "the whole hull inside starts docking");
    }

    @Test
    void signAndLegsAreSolid() {
        World w = new World();
        w.enemies.clear();
        float[] s = w.shops[0];
        w.ship.x = s[0];
        w.ship.y = s[1] + 100; // well above the sign
        w.ship.vy = -900; // dive straight down through it
        for (int i = 0; i < 40; i++) w.update(0.016f, NONE);
        assertTrue(w.ship.y > s[1] + 56, "the sign board stops the dive from above");
        assertEquals(World.UNDOCKED, w.shopState[0], "you never dock through the roof");

        w = new World();
        w.enemies.clear();
        s = w.shops[0];
        w.ship.x = s[0] - 50; // hull overlapping the left leg
        w.ship.y = s[1] - 44;
        w.update(0.016f, NONE);
        assertTrue(w.ship.x <= s[0] - 56 + 0.1f, "the left leg is solid: hull pushed clear outside");
        assertFalse(w.inDockBox(0, w.ship.x, w.ship.y), "and clipping it never counts as a dock");
        assertEquals(World.UNDOCKED, w.shopState[0]);
    }

    @Test
    void signArtIsOnDiskAt32px() throws Exception {
        File dir = null;
        for (String p : new String[] { "assets/shop", "../assets/shop" }) {
            File d = new File(p);
            if (d.isDirectory()) {
                dir = d;
                break;
            }
        }
        assertNotNull(dir, "no assets/shop found from " + new File(".").getAbsolutePath());
        String[] names = { "shop_undocked", "shop_doc1", "shop_doc2", "shop_doc3",
                "shop_doc4", "shop_doc5", "shop_doc6" };
        for (String n : names) {
            File f = new File(dir, n + ".png");
            assertTrue(f.isFile(), "missing " + n + ".png");
            BufferedImage img = ImageIO.read(f);
            assertNotNull(img, n + ".png unreadable");
            assertEquals(32, img.getWidth(), n + " width");
            assertEquals(32, img.getHeight(), n + " height");
        }
    }
}
