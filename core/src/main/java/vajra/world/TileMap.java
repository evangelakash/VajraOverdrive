package vajra.world;

/** Tile grid. Row 0 is the bottom. Outside the grid is open. IDs: 0 open sky, 1 rock, 2 gold trim, 3 dark depth. */
public class TileMap {
    public static final int TILE = 32;
    public static final int EMPTY = 0, ROCK = 1, GOLD = 2, DARK = 3;

    public final float ox;
    public final float oy;
    public final int cols, rows;
    final int[] cells; // package-visible: MapGen's post-passes sweep it directly

    public TileMap(float ox, float oy, int cols, int rows) {
        this.ox = ox;
        this.oy = oy;
        this.cols = cols;
        this.rows = rows;
        this.cells = new int[cols * rows];
    }

    public int get(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) return EMPTY;
        return cells[ty * cols + tx];
    }

    public void set(int tx, int ty, int v) {
        if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) return;
        cells[ty * cols + tx] = v;
    }

    public boolean solidPx(float x, float y) {
        int tx = (int) Math.floor((x - ox) / TILE);
        int ty = (int) Math.floor((y - oy) / TILE);
        return get(tx, ty) != EMPTY;
    }

    /** 8-neighbour autotile order: N, NE, E, SE, S, SW, W, NW. */
    public static final int[] NEIGH_DX = { 0, 1, 1, 1, 0, -1, -1, -1 };
    public static final int[] NEIGH_DY = { 1, 1, 0, -1, -1, -1, 0, 1 };

    /** Edge mask for the renderer: bit k set when the neighbour in NEIGH_DX/DY[k] is open sky
     *  (off the grid counts as open). Buried tile = 0, fully exposed = 255. */
    public int edgeMask(int tx, int ty) {
        int m = 0;
        for (int k = 0; k < 8; k++)
            if (get(tx + NEIGH_DX[k], ty + NEIGH_DY[k]) == EMPTY) m |= 1 << k;
        return m;
    }

    /** Fraction of open tiles in a square of half-side r around a pixel. */
    public float openFraction(float cx, float cy, float r) {
        int open = 0, total = 0;
        int x0 = (int) Math.floor((cx - r - ox) / TILE);
        int x1 = (int) Math.floor((cx + r - ox) / TILE);
        int y0 = (int) Math.floor((cy - r - oy) / TILE);
        int y1 = (int) Math.floor((cy + r - oy) / TILE);
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                if (tx < 0 || ty < 0 || tx >= cols || ty >= rows) continue;
                total++;
                if (get(tx, ty) == EMPTY) open++;
            }
        }
        return total == 0 ? -1 : (float) open / total;
    }

    /** Pushes a rect out of solid tiles along the smallest penetration. Returns corrected x,y.
     *  Writes into the instance scratch buffer so multiple TileMaps can't alias each other. */
    private final float[] out = new float[2];

    public float[] pushOut(float x, float y, float w, float h) {
        for (int pass = 0; pass < 4; pass++) {
            int x0 = (int) Math.floor((x - ox) / TILE);
            int x1 = (int) Math.floor((x + w - ox) / TILE);
            int y0 = (int) Math.floor((y - oy) / TILE);
            int y1 = (int) Math.floor((y + h - oy) / TILE);
            float bestX = 0, bestY = 0, best = Float.MAX_VALUE;
            for (int ty = y0; ty <= y1; ty++) {
                for (int tx = x0; tx <= x1; tx++) {
                    if (get(tx, ty) == EMPTY) continue;
                    float l = ox + tx * TILE, b = oy + ty * TILE;
                    float px = Math.min(x + w - l, l + TILE - x);
                    float py = Math.min(y + h - b, b + TILE - y);
                    if (px <= 0 || py <= 0) continue;
                    if (px < py && px < best) { best = px; bestX = (x + w / 2 < l + TILE / 2) ? -px : px; bestY = 0; }
                    else if (py <= px && py < best) { best = py; bestY = (y + h / 2 < b + TILE / 2) ? -py : py; bestX = 0; }
                }
            }
            if (best == Float.MAX_VALUE) break;
            x += bestX;
            y += bestY;
        }
        out[0] = x;
        out[1] = y;
        return out;
    }

}