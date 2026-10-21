package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.world.TileMap;

/** Walls draw the user's 16 hand-drawn Wall_Tiles as a DUAL-GRID: one tile per grid VERTEX,
 *  picked by the 2 x 2 cell pattern around that vertex (NW,NE,SW,SE) and drawn centred on the
 *  vertex at 32 px. pattern = (NW?8)|(NE?4)|(SW?2)|(SE?1) indexes P2FILE → tile_XX. Every tile
 *  border is the same half-split of the two cells it passes through, so neighbouring vertex
 *  tiles always meet (tools/fix_wall_tiles.py decodes the table and verifies every seam).
 *  Tiles draw at their NATIVE colours, no tint: the art itself carries gold trim and dark
 *  depth, the all-sky pattern draws their empty tile. A missing file falls back to flat
 *  squares, never a crash. */
public class TileImages {
    private static final int S = 16;
    private static final String DIR = "atala/walls/Wall_Tiles/tile_%02d.png";

    /** dual-grid pattern -> tile file index (derived in tools/fix_wall_tiles.py). */
    private static final int[] P2FILE = { 12, 13, 0, 3, 8, 1, 14, 5, 15, 4, 11, 2, 9, 10, 7, 6 };

    private final Texture[] tile = new Texture[16]; // null entries when the folder is missing
    private final TextureRegion[] flat = new TextureRegion[4]; // fallback squares per id

    public TileImages() {
        try {
            for (int i = 0; i < 16; i++) {
                Texture t = ImageKit.texture(String.format(DIR, i), true);
                if (t == null) throw new java.io.FileNotFoundException();
                tile[i] = t;
            }
        } catch (Throwable ignored) {
            for (int i = 0; i < 16; i++)
                if (tile[i] != null) {
                    tile[i].dispose();
                    tile[i] = null;
                }
            flat[TileMap.ROCK] = flatTex(0xE8E6E0FF);
            flat[TileMap.GOLD] = flatTex(0xFFD700FF);
            flat[TileMap.DARK] = flatTex(0x1A1A1EFF);
        }
    }

    private static TextureRegion flatTex(int rgba) {
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setColor(rgba);
        p.fill();
        Texture t = new Texture(p);
        t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        p.dispose();
        return new TextureRegion(t);
    }

    /** Draws every vertex tile touching the camera bounds. Art is 16px, drawn at 32px. */
    public void draw(SpriteBatch batch, TileMap m, float l, float b, float r, float t) {
        float T = TileMap.TILE;
        int vx0 = Math.max(0, (int) Math.ceil((l - m.ox) / T - 0.5f));
        int vx1 = Math.min(m.cols, (int) Math.floor((r - m.ox) / T + 0.4999f));
        int vy0 = Math.max(0, (int) Math.ceil((b - m.oy) / T - 0.5f));
        int vy1 = Math.min(m.rows, (int) Math.floor((t - m.oy) / T + 0.4999f));
        batch.setColor(1f, 1f, 1f, 1f); // native art colours, untinted
        for (int vy = vy0; vy <= vy1; vy++) {
            for (int vx = vx0; vx <= vx1; vx++) {
                int a = m.get(vx - 1, vy), n = m.get(vx, vy);
                int c = m.get(vx - 1, vy - 1), d = m.get(vx, vy - 1);
                int p = (a != TileMap.EMPTY ? 8 : 0) | (n != TileMap.EMPTY ? 4 : 0)
                        | (c != TileMap.EMPTY ? 2 : 0) | (d != TileMap.EMPTY ? 1 : 0);
                int id = TileMap.ROCK;
                if (a == TileMap.GOLD || n == TileMap.GOLD || c == TileMap.GOLD || d == TileMap.GOLD)
                    id = TileMap.GOLD;
                else if (a == TileMap.DARK || n == TileMap.DARK || c == TileMap.DARK || d == TileMap.DARK)
                    id = TileMap.DARK;
                float x = m.ox + vx * T - T / 2, y = m.oy + vy * T - T / 2;
                if (tile[P2FILE[p]] == null) {
                    if (p != 0) batch.draw(flat[id], x, y, T, T);
                } else batch.draw(tile[P2FILE[p]], x, y, T, T);
            }
        }
    }

    public void dispose() {
        for (Texture t : tile) if (t != null) t.dispose();
        for (TextureRegion r : flat) if (r != null) r.getTexture().dispose();
    }
}
