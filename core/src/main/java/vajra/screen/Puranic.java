package vajra.screen;

/** THE POTHI AND THE DIAL — the drawing vocabulary of every menu screen, all of it vector.
 *
 *  <p>Two ideas meet here. The first is the old book. The Bhagavata Purana was written and copied
 *  on palm leaf and bark: long narrow leaves, a line of ink incised along the grain, a hole near
 *  each end, the whole bundle (a pothi) held by a cord strung through the holes. Its fifth book
 *  describes the seven nether worlds, Atala to Patala, which are the seven lokas of this game; its
 *  eighth tells of the Vamana and the Trivikrama who broke the Vajra. So the interface is a
 *  manuscript: leaves of bark paper on a dark earth, strung on twine, set down in the soil's own
 *  colours, never in festival colours.
 *
 *  <p>The second is the instrument. Over the bark sit the marks of a modern readout: a ruler
 *  scale along the edge, reticle brackets on every panel, a dial whose twenty-four spokes are the
 *  spokes of the dharma wheel, a table of figures. Thin rules, flat colour, generous dark. The
 *  book and the instrument share one palette: laterite, black cotton soil, clay, ochre, bone,
 *  and verdigris for the one cool accent (copper turns green in the ground).
 *
 *  <p>Everything draws into an ALREADY-OPEN {@link com.badlogic.gdx.graphics.glutils.ShapeRenderer}
 *  pass (Filled) in UI space, y up. Curves are many short segments and the window is
 *  multisampled, so nothing is stair-stepped. Nothing here is a bitmap. */
public final class Puranic {
    private Puranic() {}

    // =============================================================== palette: the soil's own
    public static final float[] NIGHT = { 0.050f, 0.037f, 0.030f };      // black cotton soil: the ground
    public static final float[] EARTH = { 0.125f, 0.088f, 0.064f };      // dark umber: the lit end of the ground
    public static final float[] PLATE = { 0.150f, 0.108f, 0.078f };      // a panel: clay-umber
    public static final float[] UMBER = { 0.230f, 0.155f, 0.105f };      // raised clay
    public static final float[] BARK = { 0.390f, 0.275f, 0.185f };       // stripped bark, shaded
    public static final float[] LEAF = { 0.800f, 0.670f, 0.460f };       // palm leaf: the light page
    public static final float[] LEAF_DARK = { 0.640f, 0.510f, 0.340f };  // palm leaf in shade
    public static final float[] INK = { 0.130f, 0.075f, 0.050f };        // soot ink, incised on leaf
    public static final float[] BONE = { 0.920f, 0.860f, 0.720f };       // reading text on the dark
    public static final float[] OCHRE = { 0.880f, 0.620f, 0.270f };      // yellow ochre: the chosen, the lit
    public static final float[] AMBER = { 0.930f, 0.700f, 0.300f };      // resin: a brighter ochre
    public static final float[] TERRACOTTA = { 0.800f, 0.390f, 0.260f }; // fired clay: a loss, a warning
    public static final float[] LATERITE = { 0.560f, 0.250f, 0.170f };   // red laterite: deep accent
    public static final float[] VERDIGRIS = { 0.370f, 0.700f, 0.600f };  // oxidised copper: a gain, the cool note
    public static final float[] ASH = { 0.600f, 0.680f, 0.740f };        // wood ash: the hull you are flying
    public static final float[] COPPER = { 0.700f, 0.520f, 0.330f };     // copper: quiet line-work
    public static final float[] BRONZE = { 0.860f, 0.700f, 0.460f };     // bronze: the heading colour

    private static final long T0 = System.nanoTime();

    /** Seconds since the first call: the one clock anything that moves reads. */
    public static float time() {
        return (System.nanoTime() - T0) / 1_000_000_000f;
    }

    public static void c(Ui ui, float[] col, float a) {
        ui.shapes.setColor(col[0], col[1], col[2], a);
    }

    /** A colour between two, 0..1. */
    public static float[] mix(float[] a, float[] b, float t) {
        return new float[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t };
    }

    private static com.badlogic.gdx.graphics.Color col(float[] c, float a) {
        return new com.badlogic.gdx.graphics.Color(c[0], c[1], c[2], a);
    }

    /** A steady 0..1 from an integer: fixed "random" places that never flicker between frames. */
    public static float hash(int n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x & 0xFFFFF) / (float) 0x100000;
    }

    // =============================================================== primitives
    public static void line(Ui ui, float x0, float y0, float x1, float y1, float w) {
        ui.shapes.rectLine(x0, y0, x1, y1, w);
    }

    public static void disc(Ui ui, float x, float y, float r) {
        ui.shapes.circle(x, y, r, Math.max(10, (int) (r * 2.2f)));
    }

    public static void ring(Ui ui, float cx, float cy, float r, float w) {
        arc(ui, cx, cy, r, 0, (float) (Math.PI * 2), w, Math.max(24, (int) (r * 1.2f)));
    }

    /** A stroked arc, angles in radians. */
    public static void arc(Ui ui, float cx, float cy, float r, float a0, float a1, float w, int segs) {
        float px = cx + (float) Math.cos(a0) * r, py = cy + (float) Math.sin(a0) * r;
        for (int i = 1; i <= segs; i++) {
            float a = a0 + (a1 - a0) * i / segs;
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            ui.shapes.rectLine(px, py, x, y, w);
            px = x;
            py = y;
        }
    }

    /** A convex polygon, filled as a fan from its first point. */
    public static void fan(Ui ui, float... p) {
        for (int i = 4; i + 1 < p.length; i += 2)
            ui.shapes.triangle(p[0], p[1], p[i - 2], p[i - 1], p[i], p[i + 1]);
    }

    /** A filled rectangle with rounded ends. */
    public static void pill(Ui ui, float x, float y, float w, float h, float r) {
        r = Math.min(r, Math.min(w, h) / 2f);
        ui.shapes.rect(x + r, y, w - 2 * r, h);
        ui.shapes.rect(x, y + r, w, h - 2 * r);
        disc(ui, x + r, y + r, r);
        disc(ui, x + w - r, y + r, r);
        disc(ui, x + r, y + h - r, r);
        disc(ui, x + w - r, y + h - r, r);
    }

    /** The outline of a rounded rectangle. */
    public static void arcFrame(Ui ui, float x, float y, float w, float h, float r, float t) {
        r = Math.min(r, Math.min(w, h) / 2f);
        line(ui, x + r, y, x + w - r, y, t);
        line(ui, x + r, y + h, x + w - r, y + h, t);
        line(ui, x, y + r, x, y + h - r, t);
        line(ui, x + w, y + r, x + w, y + h - r, t);
        float hp = (float) (Math.PI / 2);
        arc(ui, x + r, y + r, r, (float) Math.PI, (float) (Math.PI + hp), t, 8);
        arc(ui, x + w - r, y + r, r, (float) (Math.PI + hp), (float) (2 * Math.PI), t, 8);
        arc(ui, x + w - r, y + h - r, r, 0, hp, t, 8);
        arc(ui, x + r, y + h - r, r, hp, (float) Math.PI, t, 8);
    }

    public static void diamond(Ui ui, float x, float y, float r) {
        ui.shapes.triangle(x - r, y, x, y + r, x + r, y);
        ui.shapes.triangle(x - r, y, x + r, y, x, y - r);
    }

    // =============================================================== bark, strata, the ground
    /** The fibres of bark or the lines of a leaf: long, thin, nearly level strokes that drift a
     *  little, at fixed places. `n` strokes in the box, drawn in `tone`. */
    public static void grain(Ui ui, float x, float y, float w, float h, int seed, int n, float a, float[] tone) {
        for (int i = 0; i < n; i++) {
            float ly = y + hash(seed + i * 3) * h;
            float x0 = x + hash(seed + i * 5) * w * 0.45f, len = w * (0.25f + 0.65f * hash(seed + i * 7));
            float drift = (hash(seed + i * 11) - 0.5f) * 5f, amp = 0.4f + hash(seed + i * 13) * 1.2f;
            c(ui, tone, a * (0.35f + 0.65f * hash(seed + i * 17)));
            float px = x0, py = ly;
            for (float d = 24; d <= len + 24; d += 24) {
                float dd = Math.min(d, len), nx = x0 + dd;
                float ny = ly + drift * dd / len + (float) Math.sin(nx * 0.021f + i) * amp;
                if (nx > x + w) break;
                ui.shapes.rectLine(px, py, nx, ny, 1f);
                px = nx;
                py = ny;
            }
        }
    }

    /** The seven strata of the soil along the foot of a screen, one for each loka, Atala at the
     *  top and Patala deepest. Thin and quiet: the ground the interface stands on. */
    public static void strata(Ui ui) {
        float[][] tone = { LATERITE, UMBER, OCHRE, BARK, LATERITE, EARTH, NIGHT };
        float[] alpha = { 0.46f, 0.55f, 0.28f, 0.52f, 0.40f, 0.70f, 0.90f };
        float[] band = { 9, 8, 6, 9, 7, 8, 5 };
        float y = 52;
        for (int i = 0; i < 7; i++) {
            y -= band[i];
            c(ui, tone[i], alpha[i]);
            ui.shapes.rect(0, y, Ui.UI_W, band[i]);
            grain(ui, 0, y, Ui.UI_W, band[i], 600 + i * 50, 7, 0.22f, NIGHT);
            c(ui, COPPER, 0.18f);
            ui.shapes.rect(0, y + band[i] - 1, Ui.UI_W, 1);
        }
        c(ui, NIGHT, 1f);
        ui.shapes.rect(0, 0, Ui.UI_W, Math.max(0, y));
    }

    /** The ground of every opaque screen: black cotton soil lit to umber toward the top, the grain
     *  of bark over it, the dial of a wheel faint behind the page, the strata along the foot. */
    public static void backdrop(Ui ui) {
        ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H,
                col(NIGHT, 1f), col(NIGHT, 1f), col(EARTH, 1f), col(EARTH, 1f));
        grain(ui, 0, 0, Ui.UI_W, Ui.UI_H, 11, 130, 0.10f, BARK);
        dial(ui, Ui.UI_W / 2f, 350, 262, 0.07f);
        strata(ui);
    }

    /** The dharma wheel read as an instrument: a ring, a ring of ticks, twenty-four spokes. */
    public static void dial(Ui ui, float cx, float cy, float r, float a) {
        c(ui, COPPER, a);
        ring(ui, cx, cy, r, 1.4f);
        ring(ui, cx, cy, r * 0.62f, 1f);
        ring(ui, cx, cy, r * 0.18f, 1f);
        for (int k = 0; k < 24; k++) {
            float ang = (float) (k * Math.PI / 12), co = (float) Math.cos(ang), si = (float) Math.sin(ang);
            line(ui, cx + co * r * 0.18f, cy + si * r * 0.18f, cx + co * r * 0.62f, cy + si * r * 0.62f, 1f);
        }
        for (int k = 0; k < 120; k++) { // the scale round the rim
            float ang = (float) (k * Math.PI / 60), co = (float) Math.cos(ang), si = (float) Math.sin(ang);
            float len = k % 5 == 0 ? 12 : 6;
            line(ui, cx + co * r, cy + si * r, cx + co * (r + len), cy + si * (r + len), 1f);
        }
    }

    /** The dharma wheel as a small mark: a ring, eight spokes, a hub. */
    public static void wheel(Ui ui, float cx, float cy, float r, float[] tone, float a) {
        c(ui, tone, a);
        ring(ui, cx, cy, r, Math.max(1f, r * 0.14f));
        for (int k = 0; k < 8; k++) {
            float ang = (float) (k * Math.PI / 4);
            line(ui, cx, cy, cx + (float) Math.cos(ang) * r, cy + (float) Math.sin(ang) * r, Math.max(1f, r * 0.12f));
        }
        disc(ui, cx, cy, r * 0.28f);
    }

    // =============================================================== the instrument: ruler, reticle
    /** A measuring scale along a horizontal edge: a baseline, a tick every 8, a long one every 40.
     *  `down` hangs the ticks below the line, otherwise they rise from it. */
    public static void ruler(Ui ui, float x0, float x1, float y, boolean down, float a) {
        c(ui, COPPER, a);
        ui.shapes.rect(x0, y, x1 - x0, 1);
        for (float x = x0; x <= x1; x += 8) {
            boolean major = ((int) ((x - x0) / 8)) % 5 == 0;
            float len = major ? 9 : 4;
            ui.shapes.rect(x, down ? y - len : y, 1, len);
        }
    }

    /** Four L-shaped brackets round a box, like a viewfinder: the sign of a panel. */
    public static void reticle(Ui ui, float x, float y, float w, float h, float len, float t) {
        ui.shapes.rect(x, y, len, t);                 ui.shapes.rect(x, y, t, len);
        ui.shapes.rect(x + w - len, y, len, t);       ui.shapes.rect(x + w - t, y, t, len);
        ui.shapes.rect(x, y + h - t, len, t);         ui.shapes.rect(x, y + h - len, t, len);
        ui.shapes.rect(x + w - len, y + h - t, len, t); ui.shapes.rect(x + w - t, y + h - len, t, len);
    }

    /** A hairline with a small wheel in the middle: the divider. `half` is its reach either side. */
    public static void divider(Ui ui, float cx, float y, float half, float a) {
        c(ui, BRONZE, a);
        line(ui, cx - half, y, cx - 16, y, 1.2f);
        line(ui, cx + 16, y, cx + half, y, 1.2f);
        wheel(ui, cx, y, 7, BRONZE, a);
        c(ui, COPPER, a);
        ui.shapes.rect(cx - half, y - 4, 1.2f, 8);
        ui.shapes.rect(cx + half - 1.2f, y - 4, 1.2f, 8);
    }

    // =============================================================== panels and leaves
    /** A panel's edge: a hairline round it and reticle brackets in each corner. */
    public static void border(Ui ui, float x, float y, float w, float h, float a) {
        c(ui, COPPER, a * 0.55f);
        ui.shapes.rect(x, y, w, 1);
        ui.shapes.rect(x, y + h - 1, w, 1);
        ui.shapes.rect(x, y, 1, h);
        ui.shapes.rect(x + w - 1, y, 1, h);
        c(ui, BRONZE, a);
        reticle(ui, x - 1, y - 1, w + 2, h + 2, Math.min(14, Math.min(w, h) * 0.25f), 2f);
    }

    /** A clay-umber panel with a faint grain and a reticle edge: the body of a card. */
    public static void plate(Ui ui, float x, float y, float w, float h, float fill, float edge) {
        c(ui, PLATE, fill);
        ui.shapes.rect(x, y, w, h);
        grain(ui, x, y, w, h, (int) (x * 3 + y), Math.max(3, (int) (h / 18)), 0.07f * fill, BARK);
        border(ui, x, y, w, h, edge);
    }

    /** One leaf of palm: a long strip with rounded ends, the grain of the leaf along it, and a hole
     *  near each end for the cord. `lit` rims it in bronze (the leaf in hand). */
    public static void leaf(Ui ui, float x, float y, float w, float h, float[] tone, float a, boolean lit) {
        c(ui, tone, a);
        pill(ui, x, y, w, h, Math.min(7, h / 2.5f));
        grain(ui, x + 6, y + 2, w - 12, h - 4, (int) (x * 7 + y * 13), 4, 0.22f * a, INK);
        float hy = y + h / 2f;
        for (int k = 0; k < 2; k++) {
            float hx = k == 0 ? x + 12 : x + w - 12;
            c(ui, INK, 0.85f * a);
            disc(ui, hx, hy, 3.4f);
            c(ui, tone, 0.9f * a);
            disc(ui, hx, hy, 1.4f);
        }
        if (lit) {
            c(ui, BRONZE, a);
            arcFrame(ui, x, y, w, h, Math.min(7, h / 2.5f), 1.5f);
        }
    }

    /** The twine of a pothi: a dark cord with a slow twist, tied off at the end. */
    public static void cord(Ui ui, float x, float y0, float y1) {
        c(ui, INK, 0.9f);
        ui.shapes.rect(x - 1.4f, y0, 2.8f, y1 - y0);
        c(ui, COPPER, 0.65f);
        for (float y = y0 + 3; y < y1; y += 6) ui.shapes.rectLine(x - 1.4f, y, x + 1.4f, y + 3, 1f);
        c(ui, INK, 1f);
        disc(ui, x, y1, 3.6f);
        c(ui, COPPER, 0.8f);
        disc(ui, x, y1, 1.6f);
    }

    // =============================================================== the HUD's own pieces
    /** A dark bark slab for the in-flight HUD: dark enough to read over the game's bright rock,
     *  a copper hairline round it, a faint grain, and a hole for the cord at each end of a long
     *  one. The HUD's panels are leaves of the same pothi as the menus, set in shade. */
    public static void slab(Ui ui, float x, float y, float w, float h, float a) {
        c(ui, NIGHT, 0.82f * a);
        pill(ui, x, y, w, h, 6);
        grain(ui, x + 4, y + 2, w - 8, h - 4, (int) (x * 5 + y * 3), Math.max(2, (int) (h / 14)), 0.10f * a, BARK);
        c(ui, COPPER, 0.55f * a);
        arcFrame(ui, x, y, w, h, 6, 1f);
        if (w > 120 && h < 56) { // a long, low slab is a leaf: it carries the cord's holes
            for (int k = 0; k < 2; k++) {
                c(ui, INK, 0.9f * a);
                disc(ui, k == 0 ? x + 8 : x + w - 8, y + h / 2f, 2.4f);
            }
        }
    }

    /** An instrument bar: a dark track, the value fill, and a tick at every tenth, like the
     *  scale of a gauge. `frac` is 0..1. */
    public static void meter(Ui ui, float x, float y, float w, float h, float frac, float[] tone, float a) {
        c(ui, NIGHT, 0.9f * a);
        ui.shapes.rect(x, y, w, h);
        frac = Math.max(0f, Math.min(1f, frac));
        if (frac > 0) {
            c(ui, tone, a);
            ui.shapes.rect(x, y, w * frac, h);
        }
        c(ui, NIGHT, 0.75f * a);
        for (int i = 1; i < 10; i++) ui.shapes.rect(x + w * i / 10f - 0.5f, y, 1, h);
        c(ui, COPPER, 0.45f * a);
        ui.shapes.rect(x, y - 1, w, 1);
        ui.shapes.rect(x, y + h, w, 1);
    }
}
