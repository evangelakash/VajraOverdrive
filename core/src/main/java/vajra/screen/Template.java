package vajra.screen;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/** The screen template every state draws with — one palette, one set of smooth faces, one layout
 *  grid and the shared forms, so the title, pause, settings, lobby and the bazaar are
 *  recognisably the same place. The look is the Puranic bazaar ({@link Puranic}): vector
 *  ornament in the shape pass, Yatra One / Rozha One / Cinzel Decorative in the batch pass.
 *
 *  <p>Pass discipline is unchanged: {@link #dim}, {@link #panelBorder} and {@link Ornament} work in
 *  the ShapeRenderer pass, everything text in the SpriteBatch pass. */
public final class Template {
    // palette — delegated to the shared theme
    public static final float GOLD_R = UiTheme.GOLD_R, GOLD_G = UiTheme.GOLD_G, GOLD_B = UiTheme.GOLD_B;
    public static final float INK_R = UiTheme.INK_R, INK_G = UiTheme.INK_G, INK_B = UiTheme.INK_B;
    public static final float A_TITLE = UiTheme.A_TITLE, A_LINE = UiTheme.A_LINE, A_HINT = UiTheme.A_HINT;

    // THE ACCENT the in-game screens still set (GameScreen paints it with the loka's colour).
    // The bazaar does not follow it: its gold is gold leaf, always. Kept so callers still compile.
    public static float accentR = GOLD_R, accentG = GOLD_G, accentB = GOLD_B;

    /** Kept for GameScreen and the hangar; the menus no longer read it. */
    public static void setAccent(float r, float g, float b) {
        accentR = r;
        accentG = g;
        accentB = b;
    }

    // layout grid — delegated to the shared theme
    public static final float COL = UiTheme.CENTER_X;
    public static final float TITLE_Y = UiTheme.TITLE_Y;
    public static final float RULE_Y = UiTheme.RULE_Y;
    public static final float ROW = UiTheme.ROW;
    public static final float PROMPT_Y = UiTheme.PROMPT_Y;
    public static final float FOOT_Y = UiTheme.FOOT_Y;
    public static final float CARD_Y = UiTheme.CARD_Y;
    public static final float CARD_SUB_Y = UiTheme.CARD_SUB_Y;

    private Template() {}

    /** The ground of a screen — shape pass. Opaque: the night street, with its skyline. Less than
     *  opaque: a veil of the same night, so what is behind (a frozen run) still shows. */
    public static void dim(Ui ui, float alpha) {
        if (alpha >= 0.999f) {
            Puranic.backdrop(ui);
            return;
        }
        Puranic.c(ui, Puranic.NIGHT, alpha);
        ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H);
    }

    /** TRACKING between heading glyphs. */
    public static final float TITLE_TRACK = 5f;

    /** The screen heading in gold, wide-tracked — batch pass. */
    public static void title(Ui ui, SpriteBatch batch, String text, float y, float alpha) {
        ui.title.setColor(Puranic.GOLD[0], Puranic.GOLD[1], Puranic.GOLD[2], alpha);
        ui.centeredSpaced(batch, ui.title, text, y, TITLE_TRACK);
        ui.title.setColor(1, 1, 1, 1);
    }

    /** The panel frame: gold leaf, double ruled, a lotus bud in each corner — shape pass. */
    public static void panelBorder(Ui ui, float x, float y, float w, float h) {
        Puranic.border(ui, x, y, w, h, 1f);
    }

    /** A sub-heading under a title — batch pass. */
    public static void sub(Ui ui, SpriteBatch batch, String text, float y, float alpha) {
        ui.menu.setColor(Puranic.IVORY[0], Puranic.IVORY[1], Puranic.IVORY[2], alpha * A_LINE);
        ui.centered(batch, ui.menu, text, y);
        ui.menu.setColor(1, 1, 1, 1);
    }

    /** One body line at any alpha — batch pass. */
    public static void line(Ui ui, SpriteBatch batch, String text, float y, float alpha) {
        ui.body.setColor(Puranic.IVORY[0], Puranic.IVORY[1], Puranic.IVORY[2], alpha);
        ui.centered(batch, ui.body, text, y);
        ui.body.setColor(1, 1, 1, 1);
    }

    /** The keys line: each KEY in ivory, its action in brass, a lotus-dot between segments, the
     *  line centred on COL at `y`. Key and value come in `(key, value)` pairs. */
    public static void keys(Ui ui, SpriteBatch batch, float y, String... kv) {
        final float in = 8, out = 12, dot = 5;
        float total = (kv.length / 2 - 1) * (out * 2 + dot);
        for (int i = 0; i < kv.length; i += 2)
            total += ui.measure(ui.small, kv[i]) + in + ui.measure(ui.small, kv[i + 1]);
        float x = COL - total / 2f;
        for (int i = 0; i < kv.length; i += 2) {
            ui.small.setColor(Puranic.IVORY[0], Puranic.IVORY[1], Puranic.IVORY[2], 0.95f);
            ui.small.draw(batch, kv[i], x, y);
            x += ui.measure(ui.small, kv[i]) + in;
            ui.small.setColor(Puranic.BRASS[0], Puranic.BRASS[1], Puranic.BRASS[2], 1f);
            ui.small.draw(batch, kv[i + 1], x, y);
            x += ui.measure(ui.small, kv[i + 1]) + out;
            if (i + 2 < kv.length) {
                ui.small.setColor(Puranic.GOLD[0], Puranic.GOLD[1], Puranic.GOLD[2], 0.7f);
                ui.small.draw(batch, "•", x, y);
                x += dot + out;
            }
        }
        ui.small.setColor(1, 1, 1, 1);
    }

    /** One menu row: bright gold when selected, ivory when idle, centred. Batch pass. The plaque
     *  behind the selected row comes from {@link Ornament#markers}, in the shape pass. */
    public static void menuRow(Ui ui, SpriteBatch batch, String label, boolean on, float y) {
        if (on) ui.menu.setColor(1f, 0.88f, 0.42f, 1f);
        else ui.menu.setColor(Puranic.IVORY[0], Puranic.IVORY[1], Puranic.IVORY[2], 0.78f);
        ui.centered(batch, ui.menu, label, y);
        ui.menu.setColor(1, 1, 1, 1);
    }

    /** Mouse hit box in UI units (the row's full extent around a baseline). */
    public static boolean box(com.badlogic.gdx.math.Vector2 m, float x0, float y0,
                              float x1, float y1) {
        return m.x >= x0 && m.x <= x1 && m.y >= y0 && m.y <= y1;
    }
}
