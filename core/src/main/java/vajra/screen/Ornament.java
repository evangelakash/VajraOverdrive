package vajra.screen;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/** The menu screens' shared ornament, in the language of the pothi and the dial: a ruler scale
 *  along the top edge, reticle brackets at the corners, a wheel-mark divider, and a palm leaf laid
 *  behind the selected row. Draws into an ALREADY-OPEN shape pass; it never begins or ends a
 *  batch.
 *
 *  <p>The method names are the ones the screens already call (frame, divider, markers,
 *  titleMarkers, diamond), so every menu picks the look up at once. */
public final class Ornament {
    private static final GlyphLayout GL = new GlyphLayout();

    private Ornament() {}

    public static void gold(Ui ui, float a) {
        Puranic.c(ui, Puranic.BRONZE, a);
    }

    /** A dark band along the top with a measuring scale on its lower edge, and reticle brackets in
     *  the four corners of the page. */
    public static void frame(Ui ui) {
        float inset = 2 * UiTheme.SAFE_MARGIN, l = inset, r = Ui.UI_W - inset, b = inset, t = Ui.UI_H - 30;
        float strip = 22;
        Puranic.c(ui, Puranic.NIGHT, 0.92f);
        ui.shapes.rect(0, Ui.UI_H - strip, Ui.UI_W, strip);
        Puranic.ruler(ui, 0, Ui.UI_W, Ui.UI_H - strip, false, 0.55f);
        Puranic.wheel(ui, Ui.UI_W / 2f, Ui.UI_H - strip / 2f, 6, Puranic.BRONZE, 0.9f);
        Puranic.c(ui, Puranic.BRONZE, 0.65f);
        Puranic.reticle(ui, l, b, r - l, t - b, 26, 2f);
    }

    /** Hairline, wheel, hairline — the divider. `half` sets its span. */
    public static void divider(Ui ui, float cx, float y, float half, float alpha) {
        Puranic.divider(ui, cx, y, half, alpha);
    }

    /** The leaf laid behind a row's label, wide enough for the spaced text. */
    public static void markers(Ui ui, float cx, String label, float rowY) {
        GL.setText(ui.menu, label);
        float capY = rowY - GL.height / 2f;
        float w = GL.width + (label.length() - 1) * Template.ROW_TRACK + 120, h = 36;
        Puranic.leaf(ui, cx - w / 2f, capY - h / 2f, w, h, Puranic.LEAF, 1f, true);
    }

    /** Wheel [HEADING] wheel — the heading's own marks, measured wide enough (tracking included)
     *  to clear it. The text is the batch pass's business. Shape pass. */
    public static void titleMarkers(Ui ui, String heading, float headingY) {
        GL.setText(ui.title, heading);
        float w = GL.width + (heading.length() - 1) * Template.TITLE_TRACK;
        float y = headingY - ui.title.getData().capHeight / 2f;
        float off = w / 2f + 32;
        Puranic.wheel(ui, Template.COL - off, y, 9, Puranic.BRONZE, 1f);
        Puranic.wheel(ui, Template.COL + off, y, 9, Puranic.BRONZE, 1f);
        Puranic.c(ui, Puranic.COPPER, 0.8f);
        ui.shapes.rect(Template.COL - off - 74, y - 0.7f, 54, 1.4f);
        ui.shapes.rect(Template.COL + off + 20, y - 0.7f, 54, 1.4f);
    }

    public static void diamond(Ui ui, float x, float y, float r) {
        Puranic.diamond(ui, x, y, r);
    }
}
