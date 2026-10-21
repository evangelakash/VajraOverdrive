package vajra.screen;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import vajra.world.Checkpoint;
import vajra.world.World;

/** THE ROUTE — the right edge. The game is one long climb up a single spine, so its map is a
 *  ruler: the whole of the world from the grand gate's floor to the boss pit, drawn as a tall
 *  scale. On it, the five bands (the current one lit), the gates between them, every shop and
 *  checkpoint, a pointer for the ship, and a bright line from the bottom up to it: how far you
 *  have climbed. At its head, a tag with the loka, the region and the yojana reading (Atala is
 *  10,000 yojanas deep, so its 19,000 px read 0 at the gate to 10,000 at the ceiling).
 *
 *  <p>It replaces the old depth tape, which only said how deep you were; this also says what is
 *  ahead. Two passes like the rest of the HUD: {@link #shapes}, then {@link #text}. */
final class HudRoute {
    static final float LOC_W = 208, LOC_H = 86, LOC_X = Ui.UI_W - 14 - LOC_W, LOC_TOP = Ui.UI_H - 14;
    /** The ruler's line, and the span it runs: the world's floor to its ceiling. */
    static final float RX = Ui.UI_W - 30, RTOP = LOC_TOP - LOC_H - 28, RBOT = 134;
    private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V" };

    /** Where a world height lands on the ruler (clamped to it). */
    static float routeY(float worldY, float worldH) {
        float f = Math.max(0f, Math.min(1f, worldY / Math.max(1f, worldH)));
        return RBOT + f * (RTOP - RBOT);
    }

    private static float worldH(World w) {
        return w.regionEnd[w.regionEnd.length - 1];
    }

    void shapes(Ui ui, World w) {
        float wh = worldH(w);
        // the location tag
        float tagH = w.currentRegionIndex() == 0 ? 62 : LOC_H; // Bhu Loka has no yojana row
        Puranic.slab(ui, LOC_X, LOC_TOP - tagH, LOC_W, tagH, 1f);
        // a dark leaf behind the ruler, so it reads over the bright rock
        Puranic.slab(ui, RX - 40, RBOT - 12, 54, RTOP - RBOT + 24, 1f);
        int cur = w.currentRegionIndex();
        for (int i = 0; i < w.regionStart.length; i++) { // the bands, the current one lit
            float y0 = routeY(w.regionStart[i], wh), y1 = routeY(w.regionEnd[i], wh);
            if (i == cur) Puranic.c(ui, Puranic.OCHRE, 0.55f);
            else Puranic.c(ui, (i & 1) == 0 ? Puranic.UMBER : Puranic.BARK, 0.8f);
            ui.shapes.rect(RX - 12, y0, 10, y1 - y0 - 1);
        }
        // the ruler: its line, its ticks every hundred yojanas of height
        Puranic.c(ui, Puranic.COPPER, 0.9f);
        ui.shapes.rect(RX - 1, RBOT, 2, RTOP - RBOT);
        for (int i = 0; i <= 20; i++) {
            float y = routeY(i * wh / 20f, wh);
            ui.shapes.rect(RX + 1, y, i % 5 == 0 ? 7 : 4, 1);
        }
        // the gates between bands
        Puranic.c(ui, Puranic.BRONZE, 1f);
        for (int i = 1; i < w.regionStart.length; i++) {
            float y = routeY(w.regionStart[i], wh);
            ui.shapes.rect(RX - 15, y - 0.8f, 20, 1.6f);
            if (i == 1 || i == w.regionStart.length - 1) Puranic.diamond(ui, RX - 20, y, 4);
        }
        // shops: a square; checkpoints: a cross, filled dim once collected
        for (float[] p : w.shops) {
            float y = routeY(p[1], wh);
            Puranic.c(ui, Puranic.VERDIGRIS, 1f);
            ui.shapes.rect(RX - 29, y - 3, 6, 1.4f);
            ui.shapes.rect(RX - 29, y + 2, 6, 1.4f);
            ui.shapes.rect(RX - 29, y - 3, 1.4f, 6);
            ui.shapes.rect(RX - 24, y - 3, 1.4f, 6);
        }
        for (Checkpoint c : w.checkpoints) {
            if (c == null) continue;
            float y = routeY(c.y, wh);
            Puranic.c(ui, Puranic.TERRACOTTA, c.collected ? 0.5f : 1f);
            ui.shapes.rectLine(RX - 29, y - 3, RX - 23, y + 3, 1.6f);
            ui.shapes.rectLine(RX - 29, y + 3, RX - 23, y - 3, 1.6f);
            if (c.collected) Puranic.disc(ui, RX - 26, y, 1.8f);
        }
        // how far you have climbed, and where you are
        float sy = routeY(w.ship.y, wh);
        Puranic.c(ui, Puranic.OCHRE, 1f);
        ui.shapes.rect(RX - 2, RBOT, 4, sy - RBOT);
        Puranic.c(ui, Puranic.BONE, 1f);
        ui.shapes.triangle(RX - 18, sy + 6, RX - 18, sy - 6, RX - 4, sy);
        Puranic.c(ui, Puranic.OCHRE, 1f);
        ui.shapes.triangle(RX - 16, sy + 3.5f, RX - 16, sy - 3.5f, RX - 8, sy);
    }

    void text(SpriteBatch b, Ui ui, World w, String loka, String region, String yojana) {
        float cap = ui.small.getCapHeight() / 2f;
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        ui.small.draw(b, loka, LOC_X + 14, LOC_TOP - 18 + cap);
        ui.body.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
        ui.body.draw(b, region, LOC_X + 14, LOC_TOP - 40 + ui.body.getCapHeight() / 2f);
        if (w.currentRegionIndex() != 0) {
            ui.small.draw(b, "YOJANA", LOC_X + 14, LOC_TOP - 68 + cap);
            ui.big.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
            ui.right(b, ui.big, yojana, LOC_X + LOC_W - 14, LOC_TOP - 68 + ui.big.getCapHeight() / 2f);
        }
        // the band numerals, right of the ruler
        float wh = worldH(w);
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 0.95f);
        for (int i = 1; i < w.regionStart.length; i++) {
            float mid = routeY((w.regionStart[i] + w.regionEnd[i]) / 2f, wh);
            ui.small.draw(b, ROMAN[i], RX + 9, mid + cap);
        }
        ui.small.setColor(1, 1, 1, 1);
        ui.body.setColor(1, 1, 1, 1);
        ui.big.setColor(1, 1, 1, 1);
    }
}
