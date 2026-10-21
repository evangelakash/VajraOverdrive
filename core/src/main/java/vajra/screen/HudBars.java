package vajra.screen;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import vajra.world.World;

/** THE VITALS SLAB — top-left. What keeps the pilot alive, as instrument meters: hull, shield,
 *  energy and the dash, each a labelled ruler-bar with its figure on the right; under them the
 *  lives as dharma wheels, the hull flown and the purse.
 *
 *  <p>Hull reads green while it is healthy, turns amber under half and clay-red under a quarter
 *  (and pulses there): the colour is the warning, so the eye need not read the number.
 *  Geometry is public: the tests sample it. */
public final class HudBars {
    public static final float X = 14, TOP = Ui.UI_H - 14, W = 292, H = 124;
    public static final float BOT = TOP - H;

    static final float PAD = 14, LABEL_W = 60;
    public static final float BAR_X = X + PAD + LABEL_W, BAR_W = 136, BAR_H = 8;
    static final float NUM_R = X + W - PAD;
    /** Row centres: hull, shield, energy, dash. */
    static final float ROW0 = TOP - 24, PITCH = 22;
    /** The lives row, under the four meters. */
    public static final float LIVES_Y = TOP - 108;

    /** The centre line of meter row i: 0 hull, 1 shield, 2 energy, 3 dash. */
    public static float rowY(int i) { return ROW0 - i * PITCH; }

    /** The colour of a hull at this health: sound, worn, failing. */
    static float[] hullTone(float f) {
        return f > 0.5f ? Puranic.VERDIGRIS : f > 0.25f ? Puranic.AMBER : Puranic.TERRACOTTA;
    }

    private static float frac(float f) {
        return Math.max(0, Math.min(1, f));
    }

    private static float dashFrac(World w) {
        float max = Math.max(0.001f, w.ship.dashCd());
        return w.ship.dashCd <= 0 ? 1f : frac(1 - w.ship.dashCd / max);
    }

    /** Shape pass: the slab, the four meters, the wheels. */
    void shapes(Ui ui, World w) {
        Puranic.slab(ui, X, BOT, W, H, 1f);
        float hf = frac(w.ship.hp / w.ship.maxHp());
        float pulse = hf <= 0.25f && hf > 0 ? 0.65f + 0.35f * (float) Math.sin(Puranic.time() * 8f) : 1f;
        Puranic.meter(ui, BAR_X, rowY(0) - BAR_H / 2f, BAR_W, BAR_H, hf, hullTone(hf), pulse);
        Puranic.meter(ui, BAR_X, rowY(1) - BAR_H / 2f, BAR_W, BAR_H,
                w.ship.shieldEnergy / w.ship.shieldCap(), Puranic.ASH, 1f);
        Puranic.meter(ui, BAR_X, rowY(2) - BAR_H / 2f, BAR_W, BAR_H, w.ship.boostEnergy / 100f, Puranic.OCHRE, 1f);
        float df = dashFrac(w);
        Puranic.meter(ui, BAR_X, rowY(3) - BAR_H / 2f, BAR_W, BAR_H, df, df >= 1f ? Puranic.BRONZE : Puranic.COPPER, 1f);
        Puranic.c(ui, Puranic.COPPER, 0.4f);
        ui.shapes.rect(X + PAD, LIVES_Y + 14, W - 2 * PAD, 1);
        for (int i = 0; i < w.lives; i++) Puranic.wheel(ui, X + PAD + 7 + i * 19, LIVES_Y - 2, 7, Puranic.BRONZE, 1f);
    }

    // the figures: rebuilt only when a value moves
    private final int[] shown = { -1, -1, -1 };
    private final String[] figure = { "", "", "" };

    private void figure(int i, int v) {
        if (shown[i] != v) {
            shown[i] = v;
            figure[i] = Integer.toString(v);
        }
    }

    /** Batch pass: labels, figures, the hull's name and the purse. */
    void text(SpriteBatch b, Ui ui, World w) {
        String[] label = { "HULL", "SHIELD", "ENERGY", "DASH" };
        float cap = ui.small.getCapHeight() / 2f, ncap = ui.num.getCapHeight() / 2f;
        figure(0, Math.round(w.ship.hp));
        figure(1, Math.round(w.ship.shieldEnergy));
        figure(2, Math.round(w.ship.boostEnergy));
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        for (int i = 0; i < label.length; i++) ui.small.draw(b, label[i], X + PAD, rowY(i) + cap);
        ui.num.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
        for (int i = 0; i < 3; i++) ui.right(b, ui.num, figure[i], NUM_R, rowY(i) + ncap);
        boolean ready = dashFrac(w) >= 1f;
        if (ready) ui.num.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
        ui.right(b, ui.num, ready ? "READY" : String.format(java.util.Locale.ROOT, "%.1f", w.ship.dashCd),
                NUM_R, rowY(3) + ncap);
        ui.num.setColor(1, 1, 1, 1);

        // the hull flown and the purse, on the lives row
        String coins = Integer.toString(w.hangar.coins);
        float cw = ui.measure(ui.small, coins);
        ui.small.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
        ui.small.draw(b, coins, NUM_R - cw, LIVES_Y - 2 + cap);
        b.draw(ui.icons.coinUi(), NUM_R - cw - 18, LIVES_Y - 2 - 7, 14, 14);
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        String name = w.ship.def.name.toUpperCase(java.util.Locale.ROOT);
        ui.right(b, ui.small, name, NUM_R - cw - 28, LIVES_Y - 2 + cap);
        ui.small.setColor(1, 1, 1, 1);
    }
}
