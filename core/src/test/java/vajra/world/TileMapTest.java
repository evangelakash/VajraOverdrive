package vajra.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

class TileMapTest {
    @Test
    void svarnaDvaraIsSymmetric() {
        TileMap m = MapGen.svarnaDvara(24, 1624, 161, 61);
        assertEquals(161, m.cols);
        assertEquals(61, m.rows);
        for (int y = 0; y < m.rows; y++)
            for (int x = 0; x < m.cols; x++)
                assertEquals(m.get(x, y), m.get(m.cols - 1 - x, y), "mirror at " + x + "," + y);
    }

    @Test
    void cavernConnectsSouthToNorth() {
        assertConnected(MapGen.svarnaDvara(24, 1624, 161, 61));
        assertConnected(MapGen.mayaVana(24, 3618, 161, 77));
        assertConnected(MapGen.svapnaMandala(24, 6112, 161, 93));
        assertConnected(MapGen.balaMukha(24, 9100, 161, 125));
        assertConnected(MapGen.balaArena(24, 17600, 161, 94));
    }

    private void assertConnected(TileMap m) {
        int mid = m.cols / 2;
        boolean[][] seen = new boolean[m.cols][m.rows];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] { mid, 0 });
        seen[mid][0] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            if (c[1] == m.rows - 1) return; // reached north edge
            int[][] dirs = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] d : dirs) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx < 0 || ny < 0 || nx >= m.cols || ny >= m.rows || seen[nx][ny]) continue;
                if (m.get(nx, ny) != TileMap.EMPTY) continue;
                seen[nx][ny] = true;
                q.add(new int[] { nx, ny });
            }
        }
        fail("south entrance cannot reach north exit");
    }

    @Test
    void moreSkyThanRock() {
        assertOpenFraction(MapGen.svarnaDvara(24, 1624, 161, 61), 0.65);
        assertOpenFraction(MapGen.mayaVana(24, 3618, 161, 77), 0.55);
        assertOpenFraction(MapGen.svapnaMandala(24, 6112, 161, 93), 0.55);
        assertOpenFraction(MapGen.balaMukha(24, 9100, 161, 125), 0.50);
        assertOpenFraction(MapGen.balaArena(24, 17600, 161, 94), 0.65);
    }

    private void assertOpenFraction(TileMap m, double min) {
        int open = 0;
        for (int y = 0; y < m.rows; y++)
            for (int x = 0; x < m.cols; x++)
                if (m.get(x, y) == TileMap.EMPTY) open++;
        double frac = (double) open / (m.cols * m.rows);
        assertTrue(frac >= min, "only " + (int) (frac * 100) + "% open, want " + (int) (min * 100) + "%+");
    }

    @Test
    void trimFollowsTheRule() {
        TileMap m = MapGen.svarnaDvara(24, 1624, 161, 61);
        for (int y = 0; y < m.rows; y++) {
            for (int x = 0; x < m.cols; x++) {
                int v = m.get(x, y);
                if (v == TileMap.GOLD) {
                    assertTrue(m.get(x + 1, y) == TileMap.EMPTY || m.get(x - 1, y) == TileMap.EMPTY
                            || m.get(x, y + 1) == TileMap.EMPTY || m.get(x, y - 1) == TileMap.EMPTY,
                            "gold without adjacent sky at " + x + "," + y);
                } else if (v == TileMap.DARK) {
                    boolean buried = true;
                    for (int dy = -2; dy <= 2 && buried; dy++)
                        for (int dx = -2; dx <= 2 && buried; dx++)
                            if (m.get(x + dx, y + dy) == TileMap.EMPTY) buried = false;
                    assertTrue(buried, "dark too close to sky at " + x + "," + y);
                }
            }
        }
    }

    @Test
    void edgeMaskCoversEveryNeighbourShape() {
        TileMap m = new TileMap(0, 0, 3, 3);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++) m.set(x, y, TileMap.ROCK);
        assertEquals(0, m.edgeMask(1, 1), "buried tile: full tile, no carve");
        assertEquals((1 << 3) | (1 << 4) | (1 << 5) | (1 << 6) | (1 << 7), m.edgeMask(0, 0),
                "off the grid counts as open");
        m.set(0, 1, TileMap.EMPTY);
        assertEquals(1 << 6, m.edgeMask(1, 1), "sky to the west only");
        m = new TileMap(0, 0, 3, 3);
        for (int y = 0; y < 3; y++)
            for (int x = 0; x < 3; x++) m.set(x, y, TileMap.ROCK);
        m.set(2, 2, TileMap.EMPTY);
        assertEquals(1 << 1, m.edgeMask(1, 1), "concave pinch: N and E solid, NE sky");
    }

    @Test
    void pushOutBlocksAtBounds() {
        TileMap m = MapGen.svarnaDvara(24, 1624, 161, 61);
        float[] p = m.pushOut(34, 2492, 24, 24);
        assertTrue(p[0] >= 56, "pushed east of west bound, got " + p[0]);
    }

    @Test
    void balaArenaHasPitAndPassages() {
        TileMap m = MapGen.balaArena(24, 17600, 161, 94);
        for (int y = 0; y < m.rows; y++)
            for (int x = 0; x < m.cols; x++)
                assertEquals(m.get(x, y), m.get(m.cols - 1 - x, y), "mirror at " + x + "," + y);
        assertEquals(TileMap.EMPTY, m.get(80, 37)); // pit centre open
        assertEquals(TileMap.EMPTY, m.get(80, 0)); // south entrance
        int rock = 0;
        for (int y = 0; y < m.rows; y++)
            for (int x = 0; x < m.cols; x++)
                if (m.get(x, y) != TileMap.EMPTY) rock++;
        assertTrue(rock > 100, "arena needs a visible ring");
    }

    @Test
    void mayaVanaIsSymmetricWithCorridor() {
        TileMap m = MapGen.mayaVana(24, 3618, 161, 77);
        assertEquals(161, m.cols);
        assertEquals(77, m.rows);
        for (int y = 0; y < m.rows; y++)
            for (int x = 0; x < m.cols; x++)
                assertEquals(m.get(x, y), m.get(m.cols - 1 - x, y), "mirror at " + x + "," + y);
        assertEquals(TileMap.EMPTY, m.get(80, 0)); // south gap
        assertEquals(TileMap.EMPTY, m.get(80, 76)); // north gap
        assertTrue(m.get(0, 38) != TileMap.EMPTY); // west bound solid
    }
}
