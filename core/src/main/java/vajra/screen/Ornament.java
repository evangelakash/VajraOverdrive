package vajra.screen;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/** The menu screens' shared ornament, in the Puranic vocabulary: the hung valance across the
 *  top, a lotus in the lower corners, a lotus divider, and a palm-leaf plaque lit behind the
 *  selected row. Draws into an ALREADY-OPEN shape pass; it never begins or ends a batch.
 *
 *  <p>The method names are the ones the screens already call (frame, divider, markers,
 *  titleMarkers, diamond), so every menu picks the new look up at once. */
public final class Ornament {
    private static final GlyphLayout GL = new GlyphLayout();

    private Ornament() {}

    public static void gold(Ui ui, float a) {
        Puranic.c(ui, Puranic.GOLD, a);
    }

    /** The valance of cloth across the top, a gold rule inset round the page, and a lotus in each
     *  lower corner. */
    public static void frame(Ui ui) {
        float inset = 2 * UiTheme.SAFE_MARGIN, l = inset, r = Ui.UI_W - inset, b = inset;
        Puranic.canopy(ui, false);
        Puranic.c(ui, Puranic.BRASS, 0.45f);
        ui.shapes.rect(l, b, r - l, 1);
        ui.shapes.rect(l, b, 1, Ui.UI_H - 100 - b);
        ui.shapes.rect(r - 1, b, 1, Ui.UI_H - 100 - b);
        Puranic.lotus(ui, l, b, 11, Puranic.GOLD, 0.9f);
        Puranic.lotus(ui, r, b, 11, Puranic.GOLD, 0.9f);
    }

    /** Line, lotus, line — the divider. `half` sets its span. */
    public static void divider(Ui ui, float cx, float y, float half, float alpha) {
        Puranic.divider(ui, cx, y, half, alpha);
    }

    /** The palm-leaf plaque lit behind a row's label, a lotus bud either side. Measures the text
     *  so the plaque fits whatever the row says. */
    public static void markers(Ui ui, float cx, String label, float rowY) {
        GL.setText(ui.menu, label);
        float capY = rowY - GL.height / 2f;
        float w = GL.width + 110, h = 34;
        float x = cx - w / 2f, y = capY - h / 2f;
        Puranic.c(ui, Puranic.SAFFRON, 0.20f);
        Puranic.pill(ui, x, y, w, h, 17);
        Puranic.c(ui, Puranic.GOLD, 0.95f);
        Puranic.arcFrame(ui, x, y, w, h, 17, 1.6f);
        Puranic.lotus(ui, x + 18, capY, 7, Puranic.MARIGOLD, 1f);
        Puranic.lotus(ui, x + w - 18, capY, 7, Puranic.MARIGOLD, 1f);
    }

    /** Lotus [HEADING] lotus — the heading's own flowers, measured wide enough (tracking
     *  included) to clear it. The text is the batch pass's business. Shape pass. */
    public static void titleMarkers(Ui ui, String heading, float headingY) {
        GL.setText(ui.title, heading);
        float w = GL.width + (heading.length() - 1) * Template.TITLE_TRACK;
        float y = headingY - ui.title.getData().capHeight / 2f;
        float off = w / 2f + 34;
        Puranic.lotus(ui, Template.COL - off, y, 11, Puranic.GOLD, 1f);
        Puranic.lotus(ui, Template.COL + off, y, 11, Puranic.GOLD, 1f);
        Puranic.c(ui, Puranic.GOLD, 0.7f);
        ui.shapes.rect(Template.COL - off - 70, y - 0.7f, 48, 1.4f);
        ui.shapes.rect(Template.COL + off + 22, y - 0.7f, 48, 1.4f);
    }

    public static void diamond(Ui ui, float x, float y, float r) {
        Puranic.diamond(ui, x, y, r);
    }
}
