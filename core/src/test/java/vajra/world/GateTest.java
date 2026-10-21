package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.Ship;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

/** The grand structure: Bhu Loka is bare, the five gate walls stand except at their doors,
 *  and a ship-sized hull can fly from the spawn to the boss arena through every doorway. */
class GateTest {
    /** y, door half-width in tiles, grand arch? — mirrors World's gateWall list. */
    private static final float[][] GATES = {
            { 1600, 7, 1 }, { 4100, 4, 0 }, { 7600, 4, 0 }, { 12000, 4, 0 }, { 17600, 6, 1 }
    };

    /** True if the full Ship.W x Ship.H box fits at (x, y) in every map. */
    private static boolean clear(World w, float x, float y) {
        float x0 = x - Ship.W / 2, x1 = x + Ship.W / 2, y0 = y - Ship.H / 2, y1 = y + Ship.H / 2;
        for (TileMap m : w.tileMaps()) {
            int tx0 = (int) Math.floor((x0 - m.ox) / 32), tx1 = (int) Math.floor((x1 - m.ox) / 32);
            int ty0 = (int) Math.floor((y0 - m.oy) / 32), ty1 = (int) Math.floor((y1 - m.oy) / 32);
            for (int ty = ty0; ty <= ty1; ty++)
                for (int tx = tx0; tx <= tx1; tx++)
                    if (tx >= 0 && ty >= 0 && tx < m.cols && ty < m.rows && m.get(tx, ty) != 0)
                        return false;
        }
        return true;
    }

    @Test
    void bhuLokaIsBareBeforeTheGate() {
        World w = new World();
        assertEquals(1600, w.regionStart[1], "Bhu Loka is band 0");
        for (float[] s : w.shops) assertTrue(s[1] >= 1600, "no shop in Bhu");
        for (Checkpoint c : w.checkpoints) assertTrue(c.y >= 1600, "no checkpoint in Bhu");
        for (Enemy e : w.enemies) assertTrue(e.homeY >= 1600, "no enemies in Bhu");
        for (float[] o : new float[][] { { 100, 1400 }, { 2600, 1400 }, { 5100, 1400 },
                { 100, 800 }, { 2600, 800 }, { 5100, 800 } })
            assertFalse(w.blockedPx(o[0], o[1]), "no walls in Bhu at " + o[0] + "," + o[1]);
    }

    @Test
    void gateWallsStandEverywhereButAtTheirDoors() {
        World w = new World();
        for (float[] g : GATES) {
            float y = g[0], half = g[1] * 32;
            assertFalse(w.blockedPx(2600, y), "door centre open at gate " + y);
            assertFalse(w.blockedPx(2600 - half + 16, y), "door west edge open");
            assertFalse(w.blockedPx(2600 + half - 16, y), "door east edge open");
            for (float x : new float[] { 100, 400, 2600 - half - 48, 2600 + half + 48, 4800, 5100 })
                assertTrue(w.blockedPx(x, y), "wall solid at " + x + ", gate " + y);
        }
    }

    /** Regression for the sealed-lintel bug: the hull must cross every gate on the centre
     *  line, from below the wall to well above it. */
    @Test
    void theHullCanCrossEveryDoorway() {
        World w = new World();
        for (float[] g : GATES)
            for (float y = g[0] - 110; y <= g[0] + 110; y += 2)
                assertTrue(clear(w, 2600, y), "hull blocked at gate " + (int) g[0] + ", y=" + (int) y);
    }

    /** Spawn (2600,800) must be able to walk 4-connected, hull-sized, to the arena floor at
     *  (2600,20000) — cells stay inside the world so the path cannot slip past a gate's end. */
    @Test
    void aShipCanFlyFromSpawnToTheBossArena() {
        World w = new World();
        int cols = (int) (w.width / 32), rows = (int) (w.height / 32) + 2;
        boolean[][] open = new boolean[cols][rows];
        for (int cy = 0; cy < rows; cy++)
            for (int cx = 0; cx < cols; cx++) {
                float x = cx * 32 + 16, y = cy * 32 + 16;
                open[cx][cy] = y >= 20 && y <= w.height - 20 && clear(w, x, y);
            }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        boolean[][] seen = new boolean[cols][rows];
        int sx = 2600 / 32, sy = 800 / 32;
        assertTrue(open[sx][sy], "spawn is open");
        q.add(new int[] { sx, sy });
        seen[sx][sy] = true;
        boolean reached = false;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[1] * 32 >= 20000) { reached = true; break; }
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= cols || ny >= rows || seen[nx][ny]) continue;
                if (!open[nx][ny]) continue;
                seen[nx][ny] = true;
                q.add(new int[] { nx, ny });
            }
        }
        assertTrue(reached, "no hull-sized path from spawn to the boss arena");
    }
}
