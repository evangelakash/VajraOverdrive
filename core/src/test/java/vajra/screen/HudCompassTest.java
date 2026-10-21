package vajra.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import vajra.world.Checkpoint;
import vajra.world.World;

/** The compass's pure decisions, headless: bearings land north-up on the ring, the nose
 *  reads back as a bearing, and the next of each kind is the nearest one AHEAD. */
class HudCompassTest {
    private static final float[] OUT = new float[2];

    @Test
    void bearingsLandNorthUp() {
        assertEquals(0f, HudCompass.bearingDeg(0, 5), 0.01f); // straight up: the ring's top
        assertEquals(90f, HudCompass.bearingDeg(5, 0), 0.01f); // east: its right
        assertEquals(180f, HudCompass.bearingDeg(0, -5), 0.01f); // behind: its bottom
        assertEquals(-90f, HudCompass.bearingDeg(-5, 0), 0.01f); // west: its left
    }

    @Test
    void theNoseReadsBackAsABearing() {
        assertEquals(0f, HudCompass.noseBearingDeg((float) (Math.PI / 2)), 0.01f); // facing north
        assertEquals(90f, HudCompass.noseBearingDeg(0f), 0.01f); // facing east
        assertEquals(-90f, HudCompass.noseBearingDeg((float) Math.PI), 0.01f); // facing west
    }

    @Test
    void theNextOfEachKindIsTheNearestAhead() {
        World w = new World();
        w.ship.x = 2600;
        w.ship.y = 800; // Bhu Loka, on the centre column

        assertTrue(HudCompass.nearestGate(OUT, w));
        assertEquals(World.ATALA_Y, OUT[1], 0.5f); // the grand gate is the first boundary up
        assertTrue(HudCompass.nearestShop(OUT, w));
        assertTrue(OUT[1] > w.ship.y);
        assertTrue(HudCompass.nearestCp(OUT, w));
        assertTrue(OUT[1] > w.ship.y);

        w.ship.y = 4300; // inside band 2
        assertTrue(HudCompass.nearestGate(OUT, w));
        assertEquals(7600f, OUT[1], 0.5f);

        w.ship.y = 20500; // the boss arena: every gate and shop is behind the ship
        assertFalse(HudCompass.nearestGate(OUT, w));
        assertFalse(HudCompass.nearestShop(OUT, w));
        for (Checkpoint c : w.checkpoints)
            if (c != null) c.collected = true;
        assertFalse(HudCompass.nearestCp(OUT, w));
    }
}
