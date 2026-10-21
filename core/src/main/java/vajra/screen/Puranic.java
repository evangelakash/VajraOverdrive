package vajra.screen;

/** THE PURANIC BAZAAR — the drawing vocabulary of every menu screen, all of it vector.
 *
 *  <p>Where it comes from. The old Indian market was a street of shops under cloth: hung
 *  valances and awnings (vitana) in saffron and vermilion, strings of marigold and mango leaf,
 *  brass bells and oil lamps on chains, and at the street's mouth a ceremonial arched gateway,
 *  the torana. Ayodhya, in the Ramayana, is a city of banners and pennants and garlands on a
 *  street sprinkled with scented water. A guild (shreni) kept its own bazaar and a state
 *  officer (the Panyadhyaksha) kept prices fair, so a price is something posted and honest.
 *  Manuscripts and paintings are framed in gold leaf on dark ground. This class turns those
 *  into shapes: nothing here is a bitmap, nothing is stair-stepped.
 *
 *  <p>Everything draws into an ALREADY-OPEN {@link com.badlogic.gdx.graphics.glutils.ShapeRenderer}
 *  pass (Filled) in UI space, y up. Curves are built from many short segments and the window runs
 *  with multisampling, so edges are smooth. Motion is a slow sway on hanging things and nothing
 *  else. */
public final class Puranic {
    private Puranic() {}

    // =============================================================== palette
    public static final float[] SINDOOR = { 0.62f, 0.09f, 0.10f };   // vermilion cloth
    public static final float[] MAROON = { 0.32f, 0.05f, 0.09f };    // deep cloth, shadow of cloth
    public static final float[] SAFFRON = { 1.00f, 0.62f, 0.12f };   // the lit, the chosen
    public static final float[] MARIGOLD = { 1.00f, 0.72f, 0.10f };  // garland flowers
    public static final float[] GOLD = { 0.96f, 0.78f, 0.30f };      // gold leaf
    public static final float[] BRASS = { 0.76f, 0.57f, 0.27f };     // brass, quiet line-work
    public static final float[] IVORY = { 0.97f, 0.92f, 0.80f };     // reading text
    public static final float[] PARCHMENT = { 0.95f, 0.88f, 0.70f }; // palm-leaf tag
    public static final float[] INK = { 0.20f, 0.11f, 0.07f };       // text on parchment
    public static final float[] INDIGO = { 0.085f, 0.065f, 0.21f };  // the night sky, lit end
    public static final float[] NIGHT = { 0.030f, 0.025f, 0.085f };  // the night sky, dark end
    public static final float[] PLATE = { 0.115f, 0.085f, 0.250f };  // a lacquered indigo panel
    public static final float[] PEACOCK = { 0.10f, 0.62f, 0.58f };   // a gain
    public static final float[] ROSE = { 0.95f, 0.42f, 0.46f };      // a loss
    public static final float[] LEAF = { 0.30f, 0.64f, 0.32f };      // mango leaf
    public static final float[] LAVENDER = { 0.66f, 0.62f, 1.00f };  // the hull you fly
    public static final float[] SANDAL = { 0.34f, 0.21f, 0.12f };    // wood of a signboard

    private static final long T0 = System.nanoTime();

    /** Seconds since the first call: the one clock every swaying thing reads. */
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

    /** A stroked quadratic curve. */
    public static void curve(Ui ui, float x0, float y0, float cx, float cy, float x1, float y1, float w, int segs) {
        float px = x0, py = y0;
        for (int i = 1; i <= segs; i++) {
            float t = i / (float) segs, u = 1 - t;
            float x = u * u * x0 + 2 * u * t * cx + t * t * x1, y = u * u * y0 + 2 * u * t * cy + t * t * y1;
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

    public static void diamond(Ui ui, float x, float y, float r) {
        ui.shapes.triangle(x - r, y, x, y + r, x + r, y);
        ui.shapes.triangle(x - r, y, x + r, y, x, y - r);
    }

    // =============================================================== the night street
    /** The backdrop of every opaque screen: a dusk sky, a few stars, a faint ring of a wheel,
     *  and the skyline of a temple city. Drawn first. */
    public static void backdrop(Ui ui) {
        ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H,
                col(NIGHT, 1f), col(NIGHT, 1f), col(INDIGO, 1f), col(INDIGO, 1f));
        // a low warm glow where the lamps of the city are
        ui.shapes.rect(0, 0, Ui.UI_W, 150,
                col(MAROON, 0.50f), col(MAROON, 0.50f), col(MAROON, 0f), col(MAROON, 0f));
        float t = time();
        for (int i = 0; i < 46; i++) { // stars: fixed places, a slow twinkle
            float x = hash(i * 7 + 1) * Ui.UI_W, y = 200 + hash(i * 13 + 5) * (Ui.UI_H - 260);
            float tw = 0.35f + 0.35f * (float) Math.sin(t * (0.6f + hash(i) ) + i);
            c(ui, IVORY, 0.10f + 0.20f * tw);
            disc(ui, x, y, 0.8f + hash(i * 3) * 0.9f);
        }
        // the wheel (chakra) behind the page: three faint rings and spokes
        float cx = Ui.UI_W / 2f, cy = 330;
        for (int i = 0; i < 3; i++) {
            c(ui, BRASS, 0.05f);
            ring(ui, cx, cy, 190 + i * 70, 1.5f);
        }
        c(ui, BRASS, 0.04f);
        for (int k = 0; k < 24; k++) {
            float a = (float) (k * Math.PI / 12);
            line(ui, cx + (float) Math.cos(a) * 190, cy + (float) Math.sin(a) * 190,
                    cx + (float) Math.cos(a) * 330, cy + (float) Math.sin(a) * 330, 1.2f);
        }
        skyline(ui);
    }

    private static com.badlogic.gdx.graphics.Color col(float[] c, float a) {
        return new com.badlogic.gdx.graphics.Color(c[0], c[1], c[2], a);
    }

    private static float hash(int n) {
        long x = n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 29;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 32;
        return (x & 0xFFFFF) / (float) 0x100000;
    }

    /** A line of shikhara towers and stupa domes along the bottom, dark against the lamp glow. */
    private static void skyline(Ui ui) {
        float[][] t = { { 60, 70, 128, 0 }, { 170, 70, 70, 1 }, { 280, 92, 156, 0 }, { 410, 60, 84, 1 },
                { 870, 60, 84, 1 }, { 990, 96, 164, 0 }, { 1110, 64, 80, 1 }, { 1210, 66, 118, 0 } };
        for (float[] s : t) {
            if (s[3] == 0) temple(ui, s[0], 0, s[1], s[2]);
            else stupa(ui, s[0], 0, s[1], s[2]);
        }
        c(ui, NIGHT, 0.95f);
        ui.shapes.rect(0, 0, Ui.UI_W, 22);
    }

    /** A shikhara: a porch, then a tower of ledged tiers narrowing in a curve to an amalaka, a
     *  pot and a pole — the profile of a north-Indian temple. */
    public static void temple(Ui ui, float cx, float baseY, float w, float h) {
        c(ui, NIGHT, 0.96f);
        float porchH = h * 0.2f;
        ui.shapes.rect(cx - w * 0.5f, baseY, w, porchH);
        ui.shapes.triangle(cx - w * 0.5f, baseY + porchH, cx + w * 0.5f, baseY + porchH, cx, baseY + porchH + w * 0.16f);
        int tiers = 8;
        float y = baseY + porchH, towerH = h * 0.62f;
        for (int i = 0; i < tiers; i++) {
            float f0 = i / (float) tiers, f1 = (i + 1) / (float) tiers;
            float w0 = w * 0.78f * (float) Math.pow(1 - f0, 0.78), w1 = w * 0.78f * (float) Math.pow(1 - f1, 0.78);
            float y0 = y + towerH * f0, y1 = y + towerH * f1;
            ui.shapes.triangle(cx - w0 / 2, y0, cx + w0 / 2, y0, cx - w1 / 2, y1);
            ui.shapes.triangle(cx + w0 / 2, y0, cx + w1 / 2, y1, cx - w1 / 2, y1);
            ui.shapes.rect(cx - w0 / 2 - 2.5f, y0, w0 + 5, 2.4f); // the ledge of each tier
        }
        float topY = y + towerH;
        disc(ui, cx, topY + 3, w * 0.085f);                     // amalaka
        ui.shapes.triangle(cx - 4, topY + w * 0.085f, cx + 4, topY + w * 0.085f, cx, topY + w * 0.085f + 11); // kalasha
        line(ui, cx, topY + w * 0.085f + 8, cx, topY + w * 0.085f + 26, 1.4f);
        c(ui, SAFFRON, 0.50f + 0.2f * (float) Math.sin(time() * 1.7f + cx)); // a lamp in the doorway
        ui.shapes.rect(cx - 3, baseY + 6, 6, porchH * 0.55f);
    }

    /** A stupa: a drum, a half-sphere dome, a railing and a finial on a pole. */
    public static void stupa(Ui ui, float cx, float baseY, float w, float h) {
        c(ui, NIGHT, 0.96f);
        ui.shapes.rect(cx - w * 0.55f, baseY, w * 1.1f, h * 0.16f);
        int segs = 24;
        float r = w * 0.5f, cy = baseY + h * 0.16f;
        float px = cx - r, py = cy;
        for (int i = 1; i <= segs; i++) {
            float a = (float) (Math.PI - Math.PI * i / segs);
            float x = cx + (float) Math.cos(a) * r, yy = cy + (float) Math.sin(a) * r * 0.95f;
            ui.shapes.triangle(cx, cy, px, py, x, yy);
            px = x;
            py = yy;
        }
        ui.shapes.rect(cx - r * 0.28f, cy + r * 0.95f, r * 0.56f, h * 0.08f); // the harmika
        line(ui, cx, cy + r * 0.95f + h * 0.08f, cx, cy + r * 0.95f + h * 0.30f, 1.6f);
        for (int k = 0; k < 3; k++) // the stacked parasols
            ui.shapes.rect(cx - (10 - k * 3), cy + r * 0.95f + h * 0.12f + k * 6, 20 - k * 6, 1.8f);
        c(ui, SAFFRON, 0.45f);
        ui.shapes.rect(cx - 2.5f, baseY + 5, 5, h * 0.09f);
    }

    // =============================================================== cloth, bells, lamps
    /** The hung valance across the top: pleated cloth, a gold hem, scalloped drops. `full` adds
     *  the bells, lamps and garlands that the title screen wears; the working screens keep the
     *  cloth and the gold only, so nothing hangs over their text. */
    public static void canopy(Ui ui, boolean full) {
        float band = 24, r = 22, top = Ui.UI_H;
        c(ui, SINDOOR, 1f);
        ui.shapes.rect(0, top - band, Ui.UI_W, band);
        for (float x = 0; x < Ui.UI_W; x += 44) { // the pleats
            c(ui, MAROON, 0.55f);
            ui.shapes.rect(x, top - band, 22, band);
        }
        float sway = (float) Math.sin(time() * 0.9f);
        int n = (int) Math.ceil(Ui.UI_W / (2 * r));
        for (int i = 0; i < n; i++) {
            float cx = r + i * 2 * r;
            c(ui, (i & 1) == 0 ? SINDOOR : SAFFRON, 1f);
            halfDisc(ui, cx, top - band, r);
            c(ui, GOLD, 0.9f);
            arc(ui, cx, top - band, r - 1, (float) Math.PI, (float) (2 * Math.PI), 1.6f, 14);
            c(ui, GOLD, 1f);
            disc(ui, cx, top - band - r - 1, 2.2f);
        }
        c(ui, GOLD, 1f);
        ui.shapes.rect(0, top - band - 1.5f, Ui.UI_W, 3);
        c(ui, BRASS, 0.9f);
        ui.shapes.rect(0, top - 2, Ui.UI_W, 2);
        if (!full) return;
        float[] bx = { 90, 220, 330, 950, 1060, 1190 };   // bells at the edges, lamps beside the title
        for (int i = 0; i < bx.length; i++) {
            float len = 40 + (i % 2) * 18;
            if (i == 2 || i == 3) lamp(ui, bx[i] + sway * 2, top - band - r, 26, i);
            else bell(ui, bx[i] + sway * 2.5f * ((i & 1) == 0 ? 1 : -1), top - band - r, len, 9);
        }
    }

    /** The lower half of a disc hanging from the line y: one scallop of the valance. */
    private static void halfDisc(Ui ui, float cx, float y, float r) {
        int segs = 18;
        float px = cx - r, py = y;
        for (int i = 1; i <= segs; i++) {
            float a = (float) (Math.PI + Math.PI * i / segs);
            float x = cx + (float) Math.cos(a) * r, yy = y + (float) Math.sin(a) * r;
            ui.shapes.triangle(cx, y, px, py, x, yy);
            px = x;
            py = yy;
        }
    }

    /** A brass bell on a chain, swinging a little. */
    public static void bell(Ui ui, float x, float topY, float chain, float r) {
        float sw = (float) Math.sin(time() * 1.3f + x * 0.01f) * 2.2f;
        float by = topY - chain;
        c(ui, BRASS, 0.8f);
        line(ui, x, topY, x + sw, by, 1.2f);
        c(ui, GOLD, 1f);
        ui.shapes.triangle(x + sw - r, by, x + sw + r, by, x + sw, by + r * 1.15f);
        ui.shapes.rect(x + sw - r, by - r * 0.7f, 2 * r, r * 0.7f);
        c(ui, BRASS, 1f);
        ui.shapes.rect(x + sw - r, by - r * 0.7f, 2 * r, 1.4f);
        disc(ui, x + sw, by - r * 0.95f, 2.3f);
        c(ui, MAROON, 0.9f);
        disc(ui, x + sw, by - r * 0.4f, 1.6f);
    }

    /** A hanging oil lamp (a diya in a brass cup) with a living flame and a soft glow. */
    public static void lamp(Ui ui, float x, float topY, float chain, int seed) {
        float sw = (float) Math.sin(time() * 1.1f + seed) * 2f;
        float ly = topY - chain;
        c(ui, BRASS, 0.8f);
        line(ui, x, topY, x - 11 + sw, ly + 6, 1.1f);
        line(ui, x, topY, x + 11 + sw, ly + 6, 1.1f);
        float fl = 0.8f + 0.2f * (float) Math.sin(time() * 9f + seed * 3);
        c(ui, SAFFRON, 0.10f);
        disc(ui, x + sw, ly + 14, 30);
        c(ui, SAFFRON, 0.16f);
        disc(ui, x + sw, ly + 14, 18);
        c(ui, GOLD, 1f);
        ui.shapes.triangle(x + sw - 13, ly + 6, x + sw + 13, ly + 6, x + sw, ly - 6);
        ui.shapes.rect(x + sw - 13, ly + 5, 26, 2.4f);
        c(ui, MARIGOLD, 1f);
        ui.shapes.triangle(x + sw - 3.5f, ly + 7, x + sw + 3.5f, ly + 7, x + sw, ly + 7 + 12 * fl);
        c(ui, IVORY, 0.9f);
        ui.shapes.triangle(x + sw - 1.5f, ly + 7, x + sw + 1.5f, ly + 7, x + sw, ly + 7 + 6 * fl);
    }

    /** A swag of marigolds and mango leaf between two points, sagging in the middle. */
    public static void garland(Ui ui, float x0, float x1, float y, float sag, int count) {
        float prevX = x0, prevY = y;
        c(ui, LEAF, 0.7f);
        for (int i = 1; i <= 32; i++) {
            float t = i / 32f, x = x0 + (x1 - x0) * t, yy = y - sag * 4 * t * (1 - t);
            line(ui, prevX, prevY, x, yy, 1.4f);
            prevX = x;
            prevY = yy;
        }
        for (int i = 0; i <= count; i++) {
            float t = i / (float) count, x = x0 + (x1 - x0) * t, yy = y - sag * 4 * t * (1 - t);
            c(ui, LEAF, 0.95f); // a mango leaf under each flower
            ui.shapes.triangle(x - 4, yy, x + 4, yy, x, yy - 10);
            c(ui, (i & 1) == 0 ? MARIGOLD : SAFFRON, 1f);
            disc(ui, x, yy + 1, 4.2f);
            c(ui, SINDOOR, 0.9f);
            disc(ui, x, yy + 1, 1.5f);
        }
    }

    // =============================================================== lotus, vajra, ornaments
    /** A lotus seen from the front: two rings of petals round a seed-head. */
    public static void lotus(Ui ui, float x, float y, float r, float[] petal, float a) {
        for (int ring = 0; ring < 2; ring++) {
            int n = 8;
            float rr = r * (ring == 0 ? 1f : 0.66f);
            float off = ring == 0 ? 0 : (float) (Math.PI / n);
            float[] tone = ring == 0 ? petal : mix(petal, IVORY, 0.45f);
            c(ui, tone, a);
            for (int i = 0; i < n; i++) {
                float ang = off + (float) (i * 2 * Math.PI / n);
                float tx = x + (float) Math.cos(ang) * rr, ty = y + (float) Math.sin(ang) * rr;
                float px = (float) -Math.sin(ang) * rr * 0.30f, py = (float) Math.cos(ang) * rr * 0.30f;
                float mx = x + (float) Math.cos(ang) * rr * 0.55f, my = y + (float) Math.sin(ang) * rr * 0.55f;
                ui.shapes.triangle(x, y, mx + px, my + py, tx, ty);
                ui.shapes.triangle(x, y, mx - px, my - py, tx, ty);
            }
        }
        c(ui, MARIGOLD, a);
        disc(ui, x, y, r * 0.2f);
    }

    /** A line, a lotus, a line: the divider. `half` is its reach either side. */
    public static void divider(Ui ui, float cx, float y, float half, float a) {
        c(ui, GOLD, a);
        line(ui, cx - half, y, cx - 18, y, 1.4f);
        line(ui, cx + 18, y, cx + half, y, 1.4f);
        lotus(ui, cx, y, 9, GOLD, a);
        diamond(ui, cx - half, y, 3);
        diamond(ui, cx + half, y, 3);
        disc(ui, cx - half * 0.55f, y, 1.8f);
        disc(ui, cx + half * 0.55f, y, 1.8f);
    }

    /** The vajra: a central bulb, a lotus at each end, four curved prongs closing to a point. */
    public static void vajra(Ui ui, float cx, float cy, float s, float[] tone, float a) {
        c(ui, tone, a);
        disc(ui, cx, cy, 7 * s);
        ui.shapes.rect(cx - 2.2f * s, cy - 14 * s, 4.4f * s, 28 * s);
        for (int side = -1; side <= 1; side += 2) {
            float baseY = cy + side * 14 * s;
            disc(ui, cx, baseY, 5.5f * s);
            float tipY = cy + side * 62 * s;
            for (int k = -1; k <= 1; k += 2) { // two outer prongs, curved
                curve(ui, cx + k * 4 * s, baseY + side * 5 * s, cx + k * 20 * s, baseY + side * 28 * s,
                        cx, tipY, 3f * s, 16);
            }
            line(ui, cx, baseY + side * 5 * s, cx, tipY, 2.6f * s); // the centre prong
            disc(ui, cx, tipY, 2.2f * s);
        }
    }

    // =============================================================== frames and tags
    /** A gold-leaf border, double-ruled, with a lotus bud in each corner: the frame of a panel,
     *  as a miniature painting is framed. */
    public static void border(Ui ui, float x, float y, float w, float h, float a) {
        c(ui, GOLD, a);
        ui.shapes.rect(x, y, w, 2);
        ui.shapes.rect(x, y + h - 2, w, 2);
        ui.shapes.rect(x, y, 2, h);
        ui.shapes.rect(x + w - 2, y, 2, h);
        c(ui, BRASS, a * 0.8f);
        ui.shapes.rect(x + 5, y + 5, w - 10, 1);
        ui.shapes.rect(x + 5, y + h - 6, w - 10, 1);
        ui.shapes.rect(x + 5, y + 5, 1, h - 10);
        ui.shapes.rect(x + w - 6, y + 5, 1, h - 10);
        lotus(ui, x, y, 8, GOLD, a);
        lotus(ui, x + w, y, 8, GOLD, a);
        lotus(ui, x, y + h, 8, GOLD, a);
        lotus(ui, x + w, y + h, 8, GOLD, a);
    }

    /** A lacquered indigo plate with a border: the body of a card. */
    public static void plate(Ui ui, float x, float y, float w, float h, float fill, float edge) {
        c(ui, PLATE, fill);
        ui.shapes.rect(x, y, w, h);
        c(ui, INDIGO, fill * 0.5f);
        ui.shapes.rect(x + 6, y + 6, w - 12, h - 12);
        border(ui, x, y, w, h, edge);
    }

    /** A palm-leaf tag hung on two cords from `hangY` down to its top edge: a price tag, a board. */
    public static void tag(Ui ui, float x, float y, float w, float h, float hangY, float[] leaf, float a, boolean lit) {
        float sway = (float) Math.sin(time() * 1.0f + x * 0.02f) * 0.6f;
        c(ui, BRASS, 0.7f * a);
        line(ui, x + 14, hangY, x + 14 + sway, y + h, 1.2f);
        line(ui, x + w - 14, hangY, x + w - 14 + sway, y + h, 1.2f);
        c(ui, leaf, a);
        pill(ui, x, y, w, h, 7);
        c(ui, lit ? GOLD : BRASS, a);
        arcFrame(ui, x, y, w, h, 7, 1.4f);
        c(ui, MAROON, a);
        disc(ui, x + 14, y + h - 7, 2.2f);
        disc(ui, x + w - 14, y + h - 7, 2.2f);
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

    // =============================================================== the torana
    /** The ratio of an arch's two arc radii to its half-width: 1.6 gives a pointed arch whose rise
     *  is about 1.48 half-widths. */
    private static final float ARCH_R = 1.6f;

    /** How high a pointed arch of this half-width rises above its springing line. */
    public static float archRise(float halfW) {
        float r = halfW * ARCH_R, d = r - halfW;
        return (float) Math.sqrt(r * r - d * d);
    }

    /** A pointed, cusped arch on two pillars: the ceremonial gateway the street opens through.
     *  The doorway is recessed (a darker plate), and the arch is ruled three deep in gold and
     *  brass, with a lotus at the apex. `baseY` is the pavement, `topY` the apex. */
    public static void torana(Ui ui, float cx, float baseY, float halfW, float topY, float a) {
        float spring = topY - archRise(halfW);
        // the doorway, recessed
        float inner = halfW - 22;
        float[] pts = archPoints(cx, spring, inner, 14);
        float[] poly = new float[pts.length + 4];
        poly[0] = cx - inner;
        poly[1] = baseY + 8;
        poly[2] = cx + inner;
        poly[3] = baseY + 8;
        System.arraycopy(pts, 0, poly, 4, pts.length);
        c(ui, NIGHT, 0.55f * a);
        fan(ui, poly);
        // pillars
        c(ui, SANDAL, a);
        ui.shapes.rect(cx - halfW - 9, baseY, 18, spring - baseY);
        ui.shapes.rect(cx + halfW - 9, baseY, 18, spring - baseY);
        c(ui, GOLD, a);
        ui.shapes.rect(cx - halfW - 9, baseY, 2.5f, spring - baseY);
        ui.shapes.rect(cx + halfW + 6.5f, baseY, 2.5f, spring - baseY);
        ui.shapes.rect(cx - halfW - 13, baseY, 26, 7);
        ui.shapes.rect(cx + halfW - 13, baseY, 26, 7);
        // three rules, outermost thickest
        for (int k = 0; k < 3; k++) {
            float hw = halfW - k * 8;
            c(ui, k == 0 ? GOLD : BRASS, a * (k == 0 ? 1f : 0.85f));
            archStroke(ui, cx, spring, hw, k == 0 ? 3.4f : 1.6f);
        }
        lotus(ui, cx, topY + 6, 12, GOLD, a);
    }

    /** The outline of a pointed arch as x,y pairs from the left pillar over the apex to the right. */
    private static float[] archPoints(float cx, float spring, float halfW, int perSide) {
        float r = halfW * ARCH_R, d = r - halfW, h = (float) Math.sqrt(r * r - d * d);
        float apex = (float) Math.atan2(h, -d);
        float[] out = new float[(perSide * 2 + 1) * 2];
        int n = 0;
        for (int i = 0; i <= perSide; i++) { // left arc, centre on the right of the arch
            float ang = (float) Math.PI + (apex - (float) Math.PI) * i / perSide;
            out[n++] = cx + d + (float) Math.cos(ang) * r;
            out[n++] = spring + (float) Math.sin(ang) * r;
        }
        float apexR = (float) Math.atan2(h, d);
        for (int i = perSide - 1; i >= 0; i--) { // right arc, centre on the left
            float ang = apexR * i / perSide;
            out[n++] = cx - d + (float) Math.cos(ang) * r;
            out[n++] = spring + (float) Math.sin(ang) * r;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static void archStroke(Ui ui, float cx, float spring, float halfW, float w) {
        float[] p = archPoints(cx, spring, halfW, 28);
        for (int i = 2; i + 1 < p.length; i += 2) ui.shapes.rectLine(p[i - 2], p[i - 1], p[i], p[i + 1], w);
    }
}
