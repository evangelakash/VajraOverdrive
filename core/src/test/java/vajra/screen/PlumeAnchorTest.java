package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.ship.Ship;
import vajra.ship.ShipDef;
import vajra.ship.Hangar;

/** WHERE THE FLAME SITS ON THE HULL.
 *
 *  <p>The plume is anchored by hand and was wrong in three ways that no screenshot caught
 *  clearly enough to notice: it anchored on the COLLISION box's half (12 px) rather than the
 *  drawn art's (20 px at SIZE 40), so it was drawn underneath the hull; it subtracted a
 *  screen-space "drop" outside the rotation term, so on a downward-facing ship it shoved the
 *  flame back into the hull instead of holding the tail; and it ignored the dip, so a hit or a
 *  shield slid the hull 5 px away from its own flame.
 *
 *  <p>{@code tailPoint} is static and allocation-free precisely so this can be pinned headless.
 */
class PlumeAnchorTest {
    private static final float[] OUT = new float[2];

    private static Ship shipAt(float facing) {
        Hangar h = new Hangar();
        Ship s = new Ship(1000, 1000, ShipDef.ALL[0], h);
        s.facing = facing;
        return s;
    }

    private static float[] tail(Ship s) {
        ShipRenderer.tailPoint(s, OUT);
        return new float[] { OUT[0], OUT[1] };
    }

    /** The anchor sits a fixed distance back along the exhaust axis, whatever the heading. */
    @Test
    void theFlameHangsAPlumeAnchorOffTheTail() {
        for (float f = 0; f < (float) (Math.PI * 2) - 0.001f; f += 0.37f) {
            Ship s = shipAt(f);
            s.dip = 0;
            float[] t = tail(s);
            float dx = t[0] - s.x, dy = t[1] - s.y;
            assertEquals(ShipRenderer.PLUME_ANCHOR, (float) Math.hypot(dx, dy), 0.01f,
                    "distance off the tail at facing " + f);
        }
    }

    /** It is BEHIND the nose, never in front: the dot product with `facing` is negative. */
    @Test
    void theFlameIsAlwaysOnTheExhaustSide() {
        for (float f = 0; f < (float) (Math.PI * 2) - 0.001f; f += 0.29f) {
            Ship s = shipAt(f);
            s.dip = 0;
            float[] t = tail(s);
            float along = (float) (Math.cos(f) * (t[0] - s.x) + Math.sin(f) * (t[1] - s.y));
            assertTrue(along < 0, "the flame is behind the nose at facing " + f + " (along=" + along + ")");
            assertEquals(-ShipRenderer.PLUME_ANCHOR, along, 0.01f,
                    "and exactly one anchor back at facing " + f);
        }
    }

    /** Heading north puts the flame below; heading south puts it above. This is the case the
     *  old screen-space drop got backwards. */
    @Test
    void theFlameFollowsTheTailAround() {
        Ship up = shipAt((float) (Math.PI / 2));
        up.dip = 0;
        assertEquals(up.y - ShipRenderer.PLUME_ANCHOR, tail(up)[1], 0.01f, "nose up: flame below");

        Ship down = shipAt((float) -Math.PI / 2);
        down.dip = 0;
        assertEquals(down.y + ShipRenderer.PLUME_ANCHOR, tail(down)[1], 0.01f,
                "nose down: flame ABOVE the hull, not below it");

        Ship right = shipAt(0f);
        right.dip = 0;
        assertEquals(right.x - ShipRenderer.PLUME_ANCHOR, tail(right)[0], 0.01f,
                "nose right: flame to the left");

        Ship left = shipAt((float) Math.PI);
        left.dip = 0;
        assertEquals(left.x + ShipRenderer.PLUME_ANCHOR, tail(left)[0], 0.01f,
                "nose left: flame to the right");
    }

    /** The anchor must clear the drawn hull, not the collision box — that was the original bug. */
    @Test
    void theFlameClearsTheDrawnArtNotJustTheHitBox() {
        float artHalf = 40f / 2f; // ShipRenderer.SIZE / 2
        assertTrue(ShipRenderer.PLUME_ANCHOR > artHalf,
                "the anchor (" + ShipRenderer.PLUME_ANCHOR + ") is past the art's edge (" + artHalf + ")");
        assertTrue(ShipRenderer.PLUME_ANCHOR > Ship.W / 2f, "and past the collision half");
        // and it must not float so far out that it detaches
        assertTrue(ShipRenderer.PLUME_ANCHOR < artHalf + 8f, "but still attached to the hull");
    }

    /** A dipped hull drags its flame down with it. */
    @Test
    void theFlameRidesTheDip() {
        Ship s = shipAt((float) (Math.PI / 2));
        s.dip = 0;
        float rest = tail(s)[1];
        s.dip = 1f;
        assertEquals(rest - ShipRenderer.DIP_PX, tail(s)[1], 0.01f,
                "a fully dipped hull takes its flame down by the same DIP_PX");
        assertEquals(ShipRenderer.drawnY(s), s.y - ShipRenderer.DIP_PX, 0.001f,
                "drawnY is the hull's actual draw position");
    }

    /** And the dip never moves the flame sideways. */
    @Test
    void theDipIsVerticalOnly() {
        Ship s = shipAt(0f); // nose right: the flame is off to one side
        s.dip = 0;
        float x0 = tail(s)[0];
        s.dip = 1f;
        assertEquals(x0, tail(s)[0], 0.01f, "dipping never shifts the flame sideways");
    }
}