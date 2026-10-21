package vajra.world;

import vajra.enemy.Enemy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** The walled compound around each shop (open courtyard, ring wall, door under the bay)
 *  and the region-graded guards that hold the Atala sites. Bhu Loka holds neither. */
class GuardTest {
    private static final float T = 32;

    /** Ring tile centre: solid, or deliberately left open because a posted enemy sits on it —
     *  World.buildCompounds skips exactly those cells (within w/2 + 16 of a home). */
    private static void assertWallOrHome(World w, float wx, float wy, String where) {
        if (w.blockedPx(wx, wy)) return;
        for (Enemy e : w.enemies)
            if (Math.abs(e.homeX - wx) < e.w / 2 + 16 && Math.abs(e.homeY - wy) < e.h / 2 + 16) return;
        fail("neither wall nor home at " + where + " (" + wx + "," + wy + ")");
    }

    @Test
    void shopCompoundIsAWallWithAnOpenCourtyardAndADoorUnderTheBay() {
        World w = new World();
        for (int i = 0; i < w.shops.length; i++) {
            float sx = w.shops[i][0], sy = w.shops[i][1];
            float ox = sx - 6 * T, oy = sy - 5.5f * T; // the 12x11 compound grid origin
            String shop = "shop " + i;
            for (int tx = 0; tx < 12; tx++)
                for (int ty = 0; ty < 11; ty++) {
                    if (tx != 0 && tx != 11 && ty != 0 && ty != 10) continue; // ring only
                    if (ty == 0 && tx >= 4 && tx <= 7) continue; // the door: you rise up through it
                    assertWallOrHome(w, ox + tx * T + 16, oy + ty * T + 16, shop + " ring " + tx + "," + ty);
                }
            // the door column and its approach are open, span under the bay
            assertFalse(w.blockedPx(sx, sy - 160), shop + " door open");
            assertFalse(w.blockedPx(sx - 48, sy - 160), shop + " door west half");
            assertFalse(w.blockedPx(sx + 48, sy - 160), shop + " door east half");
            assertFalse(w.blockedPx(sx, sy - 224), shop + " approach below");
            // the courtyard is carved clean out of whatever rock sits behind it
            for (int tx = 1; tx <= 10; tx++)
                for (int ty = 1; ty <= 9; ty++)
                    assertFalse(w.blockedPx(ox + tx * T + 16, oy + ty * T + 16),
                            shop + " courtyard at " + tx + "," + ty);
        }
    }

    /** Every Atala site holds three: a rank-1 leader and two sentries, kind graded by band.
     *  Sites index as region i+1 — Bhu Loka (region 0) has no shop, checkpoint or guard. */
    @Test
    void shopsAndCheckpointsHoldRegionRankedGuards() {
        World w = new World();
        for (int i = 0; i < w.shops.length; i++)
            assertHolds(w, w.shops[i][0], w.shops[i][1], i + 1, "shop " + i);
        for (int i = 0; i < w.checkpoints.length; i++)
            assertHolds(w, w.checkpoints[i].x, w.checkpoints[i].y, i + 1, "checkpoint " + i);
        int inBhu = 0;
        for (Enemy e : w.enemies)
            if (e.guard && e.homeY < w.regionStart[1]) inBhu++;
        assertEquals(0, inBhu, "no guards stationed in Bhu Loka");
    }

    private void assertHolds(World w, float x, float y, int region, String site) {
        int found = 0, rank1 = 0;
        for (Enemy e : w.enemies) {
            if (!e.guard || Math.abs(e.homeX - x) > 110 || Math.abs(e.homeY - y) > 110) continue;
            found++;
            if (e.rank == 1) rank1++;
            Enemy.Kind expect = e.rank == 1 ? World.GUARD[region][0] : World.GUARD[region][1];
            assertEquals(expect, e.kind, site + " guard kind for region " + region);
        }
        assertEquals(3, found, site + " holds a leader and two sentries");
        assertEquals(1, rank1, site + " has exactly one rank-1 leader");
    }
}
