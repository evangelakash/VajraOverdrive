package vajra.screen;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import vajra.world.Checkpoint;
import vajra.world.World;

/** The compass, the bottom-left corner. NORTH-UP: a world bearing lands straight on the ring
 *  and never moves while you fight — in a twin-stick ship the nose is wherever the cursor
 *  points, so a ring that turned with facing only swam. "N" pins the climb, the one
 *  direction that never changes. The centre is the SHIP; the faint hand is the nose.
 *
 *  <p>Every kind shows its NEXT subject rather than every subject: the nearest gate, shop and
 *  UNCOLLECTED checkpoint above the ship, kind-coloured — gold diamond gate, cyan square
 *  shop, pink cross checkpoint. Drawing all of them
 *  collapsed instead: the whole world shares the centre column, so from mid-column every
 *  gate landed on one ring point and the rose showed one diamond where five gates stand.
 *  The whole rose is the MINIMAP: solid rock around the ship in the tiles' own off-white,
 *  and the points of interest as kind-coloured dots at their true offsets from you,
 *  clamped to the window's edge when a POI is beyond it, so "next, which way" reads off
 *  the edge dot and "what, exactly where" reads off the map. On arrival a dot vanishes;
 *  the respawn anchor always draws, even behind the ship, dimmed with a filled centre,
 *  so "where I come back" survives the run.
 *
 *  <p>Split from {@link Hud} so the HUD file only orchestrates. Two passes, like everything
 *  else: {@link #shapes} before the batch opens, {@link #label} inside it. */
final class HudCompass {
    static final float CX = 84, CY = 84, R = 62, W = 1.6f; // corner: ring clears the 14px margin

    /** The x of every gate. Match World if it ever publishes one. */
    private static final float GATE_X = World.WORLD_CX; // the spine every gate hangs on

    /** Closer than this (squared, px) the ship is on top of a subject: no mark. */
    private static final float NEAR2 = 400;

    /** The minimap: solid rock and the points of interest around the ship, inside the ring.
     *  A one-and-a-half-screen half-window (768 px) at one-tile steps renders terrain
     *  near-contiguous, snapped to the world grid so rock stays put as you climb past it.
     *  POIs sit at their true offset, clamped to MAP_LIM when beyond the window. */
    private static final float MAP_R = 61;   // rock fills the dial to the ring line
    private static final float MAP_LIM = 57; // edge-clamped POI dots rest just inside it
    private static final float MAP_STEP = 32, MAP_SCALE = MAP_R / 768;
    private static final int MAP_N = 24;
    private static final float[] ROCKC = Puranic.LEAF_DARK;       // rock, in bark tones

    private static final int GATE = 0, SHOP = 1, CHECK = 2;
    private static final float[] NORTH = { 0.5f, 0.5f, 1f };
    private static final float[] GOLD = Puranic.BRONZE;           // the gate arches' bronze
    private static final float[] SHOPC = Puranic.VERDIGRIS;      // shops: verdigris
    private static final float[] CHECKC = Puranic.TERRACOTTA;     // checkpoints: fired clay

    private static final int RING_N = 32;
    private static final float[] RING_COS = new float[RING_N + 1], RING_SIN = new float[RING_N + 1];

    static {
        for (int i = 0; i <= RING_N; i++) { // the ring never changes: do the trig once
            float a = i * 360f / RING_N;
            RING_COS[i] = MathUtils.cosDeg(a);
            RING_SIN[i] = MathUtils.sinDeg(a);
        }
    }

    private final Ui ui;
    private final GlyphLayout layout = new GlyphLayout();
    private final float nW; // the "N" width, measured once
    private final float[] spot = new float[2]; // nearest-above scratch: x first, y second

    HudCompass(Ui ui) {
        this.ui = ui;
        this.nW = measure("N");
    }

    private float measure(String s) {
        layout.setText(ui.small, s);
        return layout.width;
    }

    /** World bearing of a direction, degrees on the ring: 0 = world up, +90 = east. */
    static float bearingDeg(float dx, float dy) {
        return MathUtils.atan2(dx, dy) * MathUtils.radiansToDegrees;
    }

    /** The ship's nose as a ring bearing. It can spin with the cursor; the ring stays put. */
    static float noseBearingDeg(float facingRad) {
        return bearingDeg(MathUtils.cos(facingRad), MathUtils.sin(facingRad));
    }

    /** The nearest gate above: the grand gate and every band boundary, all on the centre
     *  column. Writes it into out (x first, y second). */
    static boolean nearestGate(float[] out, World w) {
        float best = Float.MAX_VALUE;
        boolean found = false;
        for (int i = 1; i < w.regionStart.length; i++) { // regionStart[0] = 0 is no gate
            float gy = w.regionStart[i];
            if (gy <= w.ship.y) continue;
            if (gy < best) {
                best = gy;
                out[0] = GATE_X;
                out[1] = gy;
                found = true;
            }
        }
        return found;
    }

    /** The nearest shop above the ship (shops are bands 1-4, all below the boss gate). */
    static boolean nearestShop(float[] out, World w) {
        float best = Float.MAX_VALUE;
        boolean found = false;
        for (float[] p : w.shops) {
            if (p[1] <= w.ship.y) continue;
            if (p[1] < best) {
                best = p[1];
                out[0] = p[0];
                out[1] = p[1];
                found = true;
            }
        }
        return found;
    }

    /** The nearest UNCOLLECTED checkpoint above the ship. Fully-collected copies are the
     *  anchor's business, not the next-aim's. */
    static boolean nearestCp(float[] out, World w) {
        float best = Float.MAX_VALUE;
        boolean found = false;
        for (Checkpoint c : w.checkpoints) {
            if (c == null || c.collected || c.y <= w.ship.y) continue;
            if (c.y < best) {
                best = c.y;
                out[0] = c.x;
                out[1] = c.y;
                found = true;
            }
        }
        return found;
    }

    /** Shape pass. Always drawn — even in Bhu Loka the grand gate and north are useful. */
    void shapes(ShapeRenderer s, World w) {
        s.setColor(Puranic.NIGHT[0], Puranic.NIGHT[1], Puranic.NIGHT[2], 0.80f); // the dial's face
        s.circle(CX, CY, R + 3, 48);
        map(s, w); // the dial: rock to the ring line, drawn first so the marks ride it
        // the bezel: a ring in copper and a scale of ticks outside it, a long one every 90 degrees
        s.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 0.9f);
        for (int i = 0; i < 24; i++) {
            float c = MathUtils.cosDeg(i * 15f), sn = MathUtils.sinDeg(i * 15f), len = i % 6 == 0 ? 7 : 3.5f;
            s.rectLine(CX + c * (R + 3), CY + sn * (R + 3), CX + c * (R + 3 + len), CY + sn * (R + 3 + len), 1.2f);
        }

        s.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 0.85f); // the ring line, over the rock
        for (int i = 1; i <= RING_N; i++)
            s.rectLine(CX + RING_COS[i - 1] * R, CY + RING_SIN[i - 1] * R,
                    CX + RING_COS[i] * R, CY + RING_SIN[i] * R, W);

        s.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 0.8f); // cardinal ticks, over the rock
        for (int i = 0; i < 4; i++) {
            float c = MathUtils.cosDeg(i * 90f), sn = MathUtils.sinDeg(i * 90f);
            s.rectLine(CX + c * (R - 8), CY + sn * (R - 8), CX + c * R, CY + sn * R, W);
        }

        // you: the ship at dead centre, the nose as a faint white hand from it
        float n = noseBearingDeg(w.ship.facing);
        s.setColor(Puranic.OCHRE[0], Puranic.OCHRE[1], Puranic.OCHRE[2], 0.8f);
        s.rectLine(CX, CY, CX + MathUtils.sinDeg(n) * R, CY + MathUtils.cosDeg(n) * R, W);
        s.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
        s.circle(CX, CY, 3f, 12);

        // the next of each kind above, plus wherever you would come back to
        if (nearestGate(spot, w)) mark(s, w, GATE, GOLD, 1f, spot[0], spot[1], false);
        if (nearestShop(spot, w)) mark(s, w, SHOP, SHOPC, 1f, spot[0], spot[1], false);
        Checkpoint anchor = w.respawnCp;
        boolean anchorDrawn = false;
        if (nearestCp(spot, w)) { // the bright cross: the next checkpoint to collect
            anchorDrawn = anchor != null && anchor.x == spot[0] && anchor.y == spot[1];
            mark(s, w, CHECK, CHECKC, 1f, spot[0], spot[1], anchorDrawn);
        }
        if (anchor != null && !anchorDrawn) // the dim filled cross: from anywhere in the run
            mark(s, w, CHECK, CHECKC, 0.55f, anchor.x, anchor.y, true);
    }

    /** The minimap: solid rock in a one-screen window around the ship, snapped to the world
     *  grid so terrain stays put as you climb past it. */
    private void map(ShapeRenderer s, World w) {
        float sx = MathUtils.round(w.ship.x / MAP_STEP) * MAP_STEP;
        float sy = MathUtils.round(w.ship.y / MAP_STEP) * MAP_STEP;
        float r2 = MAP_R * MAP_R;
        float pw = MAP_STEP * MAP_SCALE; // one rock dot fills its sample's pitch
        s.setColor(ROCKC[0], ROCKC[1], ROCKC[2], 0.5f);
        for (int i = -MAP_N; i <= MAP_N; i++)
            for (int j = -MAP_N; j <= MAP_N; j++) {
                float mx = i * MAP_STEP * MAP_SCALE, my = j * MAP_STEP * MAP_SCALE;
                if (mx * mx + my * my > r2) continue; // the ring holds the window round
                if (w.blockedPx(sx + i * MAP_STEP, sy + j * MAP_STEP))
                    s.rectLine(CX + mx - pw / 2, CY + my, CX + mx + pw / 2, CY + my, pw);
            }
    }

    /** One POI in the map: kind-tinted, drawn at its true offset from the ship, clamped to
     *  the window edge when beyond it, hidden on arrival. fill adds the centre square that
     *  says "this is the anchor you would return to". */
    private void mark(ShapeRenderer s, World w, int kind, float[] tint, float strength,
                      float tx, float ty, boolean fill) {
        float dx = tx - w.ship.x, dy = ty - w.ship.y;
        if (dx * dx + dy * dy < NEAR2) return;
        float mx = dx * MAP_SCALE, my = dy * MAP_SCALE;
        float m2 = mx * mx + my * my;
        if (m2 > MAP_LIM * MAP_LIM) { // beyond the window: rest on its edge, same slope
            float k = MAP_LIM / (float) Math.sqrt(m2);
            mx *= k;
            my *= k;
        }
        s.setColor(tint[0], tint[1], tint[2], strength);
        float x = CX + mx, y = CY + my;
        switch (kind) {
            case GATE: // diamond
                s.rectLine(x - 3, y, x, y + 3, 1.75f);
                s.rectLine(x, y + 3, x + 3, y, 1.75f);
                s.rectLine(x + 3, y, x, y - 3, 1.75f);
                s.rectLine(x, y - 3, x - 3, y, 1.75f);
                break;
            case SHOP: // square
                s.rectLine(x - 3, y - 3, x + 3, y - 3, 1.75f);
                s.rectLine(x + 3, y - 3, x + 3, y + 3, 1.75f);
                s.rectLine(x + 3, y + 3, x - 3, y + 3, 1.75f);
                s.rectLine(x - 3, y + 3, x - 3, y - 3, 1.75f);
                break;
            default: // checkpoint: cross
                s.rectLine(x - 3, y - 3, x + 3, y + 3, 1.75f);
                s.rectLine(x + 3, y - 3, x - 3, y + 3, 1.75f);
                if (fill) s.rectLine(x - 1.5f, y, x + 1.5f, y, 3);
        }
    }

    /** Batch pass: "N" over the ring's top. The kinds teach themselves on the dial. */
    void label(SpriteBatch batch, World w) {
        ui.small.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
        ui.small.draw(batch, "N", CX - nW / 2f, CY + R + 26);
        ui.small.setColor(1, 1, 1, 1);
    }
}
