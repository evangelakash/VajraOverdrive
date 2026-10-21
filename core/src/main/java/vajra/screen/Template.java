package vajra.screen;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** The screen template every state draws with — one palette, one font ladder, one layout
 *  grid and the shared forms, so home, play, pause and shop are recognisably the same game.
 *  Fonts live in Ui and never change: Rajdhani SemiBold 30 (headings, wide-tracked, and the
 *  numerals), Courbe Sans (subtitles, lines, prompts — Ui.FONT_SUBHEAD/FONT_BODY). States
 *  borrow these forms and draw over the right pass: dim / panelBorder / Ornament work in the
 *  ShapeRenderer pass, everything text in the SpriteBatch pass. */
public final class Template {
    // palette — delegated to the shared theme
    public static final float GOLD_R = UiTheme.GOLD_R, GOLD_G = UiTheme.GOLD_G, GOLD_B = UiTheme.GOLD_B;
    public static final float INK_R = UiTheme.INK_R, INK_G = UiTheme.INK_G, INK_B = UiTheme.INK_B;
    public static final float A_TITLE = UiTheme.A_TITLE, A_LINE = UiTheme.A_LINE, A_HINT = UiTheme.A_HINT;

    // THE ACCENT every piece of gold line-work wears. Brand gold on the menu screens; while a
    // run is up, GameScreen sets it to the loka's own colour code (the tatva its shards pay),
    // so pause, hangar and settings follow the loka you are in.
    public static float accentR = GOLD_R, accentG = GOLD_G, accentB = GOLD_B;

    /** Swap the line-work accent for the brand gold. */
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

    /** Full-screen scrim — shape pass. */
    public static void dim(Ui ui, float alpha) {
        ui.shapes.setColor(INK_R, INK_G, INK_B, alpha);
        ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H);
    }

    /** TRACKING between heading glyphs: the wide sci-fi caps the title font leans into. */
    public static final float TITLE_TRACK = 7f;

    /** Big accent heading, wide-tracked — batch pass. */
    public static void title(Ui ui, SpriteBatch batch, String text, float y, float alpha) {
        ui.title.setColor(accentR, accentG, accentB, alpha);
        ui.centeredSpaced(batch, ui.title, text, y, TITLE_TRACK);
        ui.title.setColor(1, 1, 1, 1);
    }

    /** THE PANEL BORDER: a 1 px hairline with bright squared corner ticks — shape pass.
     *  Replaces the rounded nine-slice frame; the square ticks match the screen brackets the
     *  menus already carry, so every edge in the game is the same edge. */
    public static void panelBorder(Ui ui, float x, float y, float w, float h) {
        ui.shapes.setColor(accentR, accentG, accentB, 0.6f);
        ui.shapes.rect(x, y, w, 1);                 // bottom hairline
        ui.shapes.rect(x, y + h - 1, w, 1);         // top
        ui.shapes.rect(x, y, 1, h);                 // left
        ui.shapes.rect(x + w - 1, y, 1, h);         // right
        float t = 12, k = 2; // tick length, tick thickness
        ui.shapes.setColor(accentR, accentG, accentB, 1f);
        ui.shapes.rect(x, y, t, k);
        ui.shapes.rect(x, y, k, t);
        ui.shapes.rect(x + w - t, y, t, k);
        ui.shapes.rect(x + w - k, y, k, t);
        ui.shapes.rect(x, y + h - k, t, k);
        ui.shapes.rect(x, y + h - t, k, t);
        ui.shapes.rect(x + w - t, y + h - k, t, k);
        ui.shapes.rect(x + w - k, y + h - t, k, t);
    }

    /** Rozha subtitle under a title — batch pass. */
    public static void sub(Ui ui, SpriteBatch batch, String text, float y, float alpha) {
        ui.menu.setColor(1, 1, 1, alpha * A_LINE);
        ui.centered(batch, ui.menu, text, y);
        ui.menu.setColor(1, 1, 1, 1);
    }

    /** One Oxanium body line at any alpha — batch pass. */
    public static void line(Ui ui, SpriteBatch batch, String text, float y, float alpha) {
        ui.hud.setColor(1, 1, 1, alpha);
        ui.centered(batch, ui.hud, text, y);
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** The keys line: each KEY whitish, its action greyish, a dim '·' between segments, the
     *  line centred on COL at `y`. Key and value come in `(key, value)` pairs. */
    public static void keys(Ui ui, SpriteBatch batch, float y, String... kv) {
        final float in = 8, out = 12, dot = 5;
        float total = (kv.length / 2 - 1) * (out * 2 + dot);
        for (int i = 0; i < kv.length; i += 2)
            total += ui.measure(ui.hud, kv[i]) + in + ui.measure(ui.hud, kv[i + 1]);
        float x = COL - total / 2f;
        for (int i = 0; i < kv.length; i += 2) {
            ui.hud.setColor(1, 1, 1, 0.85f);
            ui.hud.draw(batch, kv[i], x, y);
            x += ui.measure(ui.hud, kv[i]) + in;
            ui.hud.setColor(1, 1, 1, 0.5f);
            ui.hud.draw(batch, kv[i + 1], x, y);
            x += ui.measure(ui.hud, kv[i + 1]) + out;
            if (i + 2 < kv.length) {
                ui.hud.setColor(1, 1, 1, 0.3f);
                ui.hud.draw(batch, "\u00B7", x, y);
                x += dot + out;
            }
        }
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** One menu row: the accent when selected, plain white when idle, centred. Batch pass.
     *  The diamond markers beside it come from Ornament, in the shape pass. */
    public static void menuRow(Ui ui, SpriteBatch batch, String label, boolean on, float y) {
        ui.hud.setColor(on ? accentR : 1, on ? accentG : 1, on ? accentB : 1,
                on ? A_TITLE : A_LINE);
        ui.centered(batch, ui.hud, label, y);
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** Mouse hit box in UI units (the row's full extent around a baseline). */
    public static boolean box(com.badlogic.gdx.math.Vector2 m, float x0, float y0,
                              float x1, float y1) {
        return m.x >= x0 && m.x <= x1 && m.y >= y0 && m.y <= y1;
    }
}
