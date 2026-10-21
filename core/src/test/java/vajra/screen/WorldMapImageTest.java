package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.world.Checkpoint;
import vajra.world.TileMap;
import vajra.world.World;

/** The world map image: a 1:8 miniature of the LIVE world — walls drawn from the user's 16
 *  dual-grid vertex tiles (Wall_Tiles), empty sky black, shops/checkpoints/spawn in flat
 *  colours, every loka distance labelled. Writes tools/atala_map.png; the assertion is that
 *  it came out complete and at the right size, and that all 16 tiles were found. */
class WorldMapImageTest {
    private static final float S = 8; // world px per image px: a 32px tile becomes a 4px square
    private static final int ML = 130, MT = 40, MR = 170, MB = 70;
    private static final Font TITLE = new Font("SansSerif", Font.BOLD, 15);
    private static final Font LABEL = new Font("SansSerif", Font.BOLD, 13);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 12);
    /** dual-grid pattern -> tile file (same table as TileImages). */
    private static final int[] P2FILE = { 12, 13, 0, 3, 8, 1, 14, 5, 15, 4, 11, 2, 9, 10, 7, 6 };

    @Test
    void drawsTheWholeWorldFromLiveData() throws Exception {
        World w = new World();
        int mw = (int) (w.width / S), mh = (int) (w.height / S);
        BufferedImage img = new BufferedImage(ML + mw + MR, MT + mh + MB, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        BufferedImage[] wt = tiles();
        for (TileMap m : w.tileMaps())
            for (int vy = 0; vy <= m.rows; vy++)
                for (int vx = 0; vx <= m.cols; vx++) {
                    int a = m.get(vx - 1, vy), n = m.get(vx, vy);
                    int c = m.get(vx - 1, vy - 1), d = m.get(vx, vy - 1);
                    int p = (a != TileMap.EMPTY ? 8 : 0) | (n != TileMap.EMPTY ? 4 : 0)
                            | (c != TileMap.EMPTY ? 2 : 0) | (d != TileMap.EMPTY ? 1 : 0);
                    float wx = m.ox + vx * TileMap.TILE - TileMap.TILE / 2f;
                    float wy = m.oy + vy * TileMap.TILE - TileMap.TILE / 2f;
                    int px = ML + Math.round(wx / S);
                    int py = MT + Math.round((w.height - (wy + TileMap.TILE)) / S);
                    g.drawImage(wt[P2FILE[p]], px, py, 4, 4, null);
                }

        for (float[] s : w.shops) marker(g, w, s[0], s[1], Color.RED);
        for (Checkpoint c : w.checkpoints) marker(g, w, c.x, c.y, new Color(0, 200, 255));
        marker(g, w, w.width / 2, 800, new Color(40, 220, 90));

        g.setColor(Color.WHITE);
        g.setFont(TITLE);
        g.drawString("ATALA LOKA - full world map", ML, 26);
        g.setFont(SMALL);
        g.drawString("scale 1:8   one square = 32 px   black = open sky", ML + 300, 26);

        loka(g, w, "BHU LOKA", 0, "0 - 1600 px", "(1,600 px)", new Color(150, 150, 150));
        loka(g, w, "ATALA LOKA", 1, "1600 - 20600 px", "(19,000 px)", new Color(255, 215, 90));
        for (int i = 0; i < w.regionNames.length; i++) {
            int mid = (int) (w.regionStart[i] + (w.regionEnd[i] - w.regionStart[i]) / 2);
            int y = MT + Math.round((w.height - mid) / S);
            g.setFont(LABEL);
            g.setColor(i == 0 ? new Color(150, 150, 150) : Color.WHITE);
            g.drawString(w.regionNames[i], ML + mw + 14, y);
            g.setFont(SMALL);
            g.setColor(new Color(190, 190, 190));
            g.drawString(((int) (w.regionEnd[i] - w.regionStart[i])) + " px", ML + mw + 14, y + 15);
        }

        legend(g, wt[6], ML, MT + mh + 22); // tile_06 = solid wall
        g.dispose();

        File out = file("tools/atala_map.png");
        ImageIO.write(img, "png", out);
        BufferedImage back = ImageIO.read(out);
        assertNotNull(back, "map unreadable");
        assertEquals(950, back.getWidth(), "map width");
        assertEquals(2685, back.getHeight(), "map height");
    }

    private static void marker(Graphics2D g, World w, float x, float y, Color c) {
        int px = ML + Math.round(x / S), py = MT + Math.round((w.height - y) / S);
        g.setColor(c);
        g.fillRect(px - 3, py - 3, 7, 7);
    }

    private static void loka(Graphics2D g, World w, String name, int from, String line1, String line2, Color c) {
        float top = w.regionStart[from];
        float bot = from == 0 ? w.regionEnd[0] : w.height;
        int y = MT + Math.round((w.height - (top + bot) / 2) / S);
        g.setFont(LABEL);
        g.setColor(c);
        g.drawString(name, 8, y - 8);
        g.setFont(SMALL);
        g.setColor(new Color(210, 210, 210));
        g.drawString(line1, 8, y + 8);
        g.drawString(line2, 8, y + 22);
    }

    private static void legend(Graphics2D g, BufferedImage brick, int x, int y) {
        g.setFont(SMALL);
        g.drawImage(brick, x, y - 12, 12, 12, null);
        g.setColor(Color.WHITE);
        g.drawString("wall (actual brick art)", x + 16, y);
        swatch(g, Color.RED, x + 190, y - 12, "SHOP");
        swatch(g, new Color(0, 200, 255), x + 280, y - 12, "CHECKPOINT");
        swatch(g, new Color(40, 220, 90), x + 410, y - 12, "SPAWN");
    }

    private static void swatch(Graphics2D g, Color c, int x, int y, String label) {
        g.setColor(c);
        g.fillRect(x, y, 12, 12);
        g.setColor(Color.WHITE);
        g.drawString(label, x + 16, y + 12);
    }

    private static BufferedImage brick(String name) throws Exception {
        for (String p : new String[] { "../assets/atala/walls/", "assets/atala/walls/" }) {
            File f = new File(p + name);
            if (f.isFile()) return ImageIO.read(f);
        }
        fail("missing brick art " + name);
        return null;
    }

    /** The user's 16 dual-grid vertex tiles, in file order; fails if any is missing. */
    private static BufferedImage[] tiles() throws Exception {
        BufferedImage[] t = new BufferedImage[16];
        for (int i = 0; i < 16; i++) t[i] = brick(String.format("Wall_Tiles/tile_%02d.png", i));
        return t;
    }

    private static File file(String rel) {
        for (String p : new String[] { "../" + rel, rel }) {
            File f = new File(p);
            if (f.getParentFile().isDirectory()) return f;
        }
        fail("no directory for " + rel);
        return null;
    }
}
