package vajra.screen;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;

/** The title screen's ornament: a hairline frame with corner brackets, a line-diamond-line
 *  divider, and diamond markers flanking the selected row. All of it gold on ink, every stroke a
 *  hairline at low alpha, so the content stays the loudest thing on screen.
 *
 *  <p>These were private methods on HomeScreen. PauseScreen wants the same three pieces, and two
 *  hand-copied sets of ornament geometry drift the moment one of them is tuned — so it lives here
 *  once and both screens call it. Draws into an ALREADY-OPEN shape pass; it never begins or ends
 *  a batch.
 *
 *  <p>A ShapeRenderer in Filled mode has no outline, so rings and diamonds are built from
 *  primitives. */
public final class Ornament {
    private static final float LINE = 1.5f;  // hairline thickness
    private static final float FRAME_INSET = 2 * UiTheme.SAFE_MARGIN; // the frame sits twice the safe margin in

    private static final float FRAME_LEN = 44;

    // markers measure their own text; one shared layout, read immediately
    private static final GlyphLayout GL = new GlyphLayout();

    private Ornament() {}

    public static void gold(Ui ui, float a) {
        ui.shapes.setColor(Template.accentR, Template.accentG, Template.accentB, a);
    }

    /** Four corner brackets, each with a small diamond tucked into the bend. */
    public static void frame(Ui ui) {
        float l = FRAME_INSET, r = Ui.UI_W - FRAME_INSET, b = FRAME_INSET, t = Ui.UI_H - FRAME_INSET;
        gold(ui, 0.5f);
        corner(ui, l, b, 1, 1);
        corner(ui, r, b, -1, 1);
        corner(ui, l, t, 1, -1);
        corner(ui, r, t, -1, -1);
    }

    private static void corner(Ui ui, float x, float y, float dx, float dy) {
        ui.shapes.rectLine(x, y, x + dx * FRAME_LEN, y, LINE);
        ui.shapes.rectLine(x, y, x, y + dy * FRAME_LEN, LINE);
        diamond(ui, x + dx * 12, y + dy * 12, 3);
    }

    /** Line, small dots, diamond, small dots, line — the divider. `half` sets its span. */
    public static void divider(Ui ui, float cx, float y, float half, float alpha) {
        gold(ui, alpha);
        ui.shapes.rectLine(cx - half, y, cx - 30, y, LINE);
        ui.shapes.rectLine(cx + 30, y, cx + half, y, LINE);
        diamond(ui, cx, y, 7);
        diamond(ui, cx - 17, y, 2.5f);
        diamond(ui, cx + 17, y, 2.5f);
        diamond(ui, cx - half, y, 3);
        diamond(ui, cx + half, y, 3);
    }

    /** A gold diamond either side of a row's label, centred on its cap height. Measures the text
     *  so the diamonds sit clear of whatever the row says. */
    public static void markers(Ui ui, float cx, String label, float rowY) {
        GL.setText(ui.hud, label);
        float y = rowY - GL.height / 2f;
        float off = GL.width / 2f + 26;
        gold(ui, Template.A_TITLE);
        diamond(ui, cx - off, y, 6);
        diamond(ui, cx + off, y, 6);
        gold(ui, 0.5f);
        diamond(ui, cx - off - 16, y, 2.5f);
        diamond(ui, cx + off + 16, y, 2.5f);
    }

    /** Diamond [HEADING] diamond — the heading's own markers, flanking the wide-tracked title
     *  whose baseline is `headingY`. The title text is the batch pass's business; this lands
     *  only the diamonds, measured wide enough (tracking included) to clear it. Shape pass. */
    public static void titleMarkers(Ui ui, String heading, float headingY) {
        GL.setText(ui.title, heading);
        float w = GL.width + (heading.length() - 1) * Template.TITLE_TRACK;
        float y = headingY - ui.title.getData().capHeight / 2f;
        float off = w / 2f + 30;
        gold(ui, Template.A_TITLE);
        diamond(ui, Template.COL - off, y, 8);
        diamond(ui, Template.COL + off, y, 8);
        gold(ui, 0.5f);
        diamond(ui, Template.COL - off - 18, y, 3);
        diamond(ui, Template.COL + off + 18, y, 3);
    }

    public static void diamond(Ui ui, float x, float y, float r) {
        ui.shapes.triangle(x - r, y, x, y + r, x + r, y);
        ui.shapes.triangle(x - r, y, x + r, y, x, y - r);
    }
}