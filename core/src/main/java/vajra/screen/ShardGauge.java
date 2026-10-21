package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.ship.Hangar;
import vajra.world.LokaTable;
import vajra.world.World;

/** The bottom-right shard readout: one row per Pancha Tatva THIS loka pays.
 *
 *  <p>Not five rows. Atala pays Akasha alone, so drawing the other four would be four dead
 *  lines and a lie about where the player's shards come from. The block grows only where the
 *  world does - Rasatala pays two elements and gets two rows, Patala pays all five and gets
 *  five - and `LokaTable.at(loka).shards` is the single source for which.
 *
 *  <p><b>A row is one thing, read left to right: the track, the count, the gem.</b>
 *
 *  <ul>
 *    <li><b>The track</b> is progress toward the next Astra release, in {@link #SEGMENTS}
 *        segments of one shard each. The segment you are part-way through fills partially, so
 *        "3 of 5" and "4.4 of 5" are both legible without a second number. <b>The shape says
 *        which of the two jobs it is doing:</b> before the Astra is forged the segments are
 *        <b>hollow sockets</b> - empty frames you are gathering into; once forged they are
 *        <b>solid cells</b> filling up toward a release. Same five slots, same fill, two
 *        different symbols, and the corner changes the moment the weapon exists. It used to be
 *        five pips for a rank PLUS a separate bar underneath for the forge: two tracks, two
 *        questions, and no way to tell which one mattered. There is one weapon, so one track.</li>
 *    <li><b>The count</b> is how many shards you hold. Kept because they sell for coins and
 *        that is a real decision.</li>
 *    <li><b>The gem</b> is which element this row is, drawn UNTINTED - its own art colour,
 *        matching the pickup out in the world rather than TATVA_RGB (shard_1.png is cyan where
 *        Akasha is violet, and forcing the element colour here would make the corner disagree
 *        with the floor).</li>
 *  </ul>
 *
 *  <p>A gold hairline round the row means <b>a release is in hand: press F</b>. That is the one
 *  state worth shouting about, and there is exactly one thing it shouts about. While the charge
 *  is running the whole track burns brighter as it builds, so the corner you have been watching
 *  all run is the corner that tells you the Astra is going off.
 *
 *  <p>Read-only. Nothing here is spendable - the hangar owns every purchase.
 *
 *  <p>Drawn in two passes like the rest of the HUD: {@link #shapes} inside the existing shape
 *  pass, {@link #draw} inside the batch pass. */
public final class ShardGauge {
    // Layout, in UI units, y up. Mirrors the weapon card's 14 px margin at the other corner;
    // the yojana tape owns the right wall above y 120, so the whole block lives under it.
    public static final float MARGIN = 14;
    /** The gem, same 36 px face the weapon plate shows: the two corners of the HUD read at
     *  one size. The 16 px source is nearest-filtered, so 2.25x stays crisp. */
    public static final float GEM = 36;
    public static final float COUNT_W = 26;              // the shard count's column
    public static final float SEG_W = 6, SEG_H = 6, SEG_GAP = 4;
    /** Five, because both FORGE_COST and CHARGE_COST are five: the same five segments read
     *  "shards to forge it" and "shards to the next release" with no label change. */
    public static final int SEGMENTS = 5;
    public static final float TRACK_W = SEGMENTS * SEG_W + (SEGMENTS - 1) * SEG_GAP;
    public static final float ROW_H = GEM, ROW_GAP = 4;

    /** Width of one row, measured from its left edge to the screen's right margin. */
    public static final float ROW_W = GEM + 8 + COUNT_W + 4 + TRACK_W;
    /** Height of the whole block for `n` rows. */
    public static float height(int rows) {
        return rows * ROW_H + (rows - 1) * ROW_GAP;
    }

    private final Ui ui;
    private final Texture[] tex = new Texture[World.TATVA.length];
    private final TextureRegion[] reg = new TextureRegion[World.TATVA.length];
    private final float[] popAt = new float[World.TATVA.length]; // world time of the last drop
    private final int[] lastCount = new int[World.TATVA.length];
    private static final float FLASH = 0.25f;                   // seconds

    public ShardGauge(Ui ui) {
        this.ui = ui;
        for (int i = 0; i < tex.length; i++) {
            Texture t = ImageKit.texture("shards/shard_" + (i + 1) + ".png", true); // pixel art, 1x
            if (t == null) {
                // same diamond placeholder the pickup draws, so a missing crop stays soft
                Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
                float[] c = World.TATVA_RGB[i];
                p.setColor(c[0], c[1], c[2], 1f);
                p.fillCircle(8, 8, 4);
                p.fillRectangle(7, 3, 2, 10);
                p.fillRectangle(3, 7, 10, 2);
                t = new Texture(p);
                p.dispose();
            }
            tex[i] = t;
            reg[i] = new TextureRegion(t);
            popAt[i] = -999f;
        }
    }

    /** The elements this run can actually be paid in: the loka's own, in pancha order. */
    public static int[] paidElements(World w) {
        return LokaTable.at(w.lokaIndex).shards;
    }

    /** Flash the row whose count just went up. Watching the counts rather than being told keeps
     *  the world layer free of any knowledge of the HUD, and picks up every route into
     *  `addShard` for free. Timed off world.time() so it needs no dt of its own. */
    public void update(World w) {
        Hangar h = w.hangar;
        float now = w.time();
        for (int t = 0; t < lastCount.length; t++) {
            if (h.shards[t] > lastCount[t]) popAt[t] = now;
            lastCount[t] = h.shards[t];
        }
    }

    /** 1 at the moment of the drop, easing to 0 over FLASH. */
    private float flash(World w, int t) {
        float age = w.time() - popAt[t];
        return age < 0 || age > FLASH ? 0f : 1f - age / FLASH;
    }

    /** Progress toward the next release, in segment units (so 1.0 is a full track).
     *
     *  <p>It reads the shard PILE, not `forgeProgress`. Forging banks shards; it never spends
     *  them, so the pile is exactly "how many releases this harvest is worth". Reading the
     *  banked counter instead would jump to zero the instant the weapon forged, while the
     *  count column beside it still read five - two numbers disagreeing about the same five
     *  shards. Selling shards is the only thing that pulls this back down, and it should.
     *
     *  <p>Every element the loka pays feeds the same forge, so a row's own count is its share
     *  of the progress - which is why Rasatala's two rows both move. */
    public static float progress(Hangar h, World w, int t) {
        int need = h.forgeNeed(w.lokaIndex);
        // shards/need is the fraction of the COST, and SEGMENTS is the whole track - so a full
        // cost is a full track, and because need == SEGMENTS each segment is one shard.
        return need <= 0 ? SEGMENTS : h.shards[t] / (float) need * SEGMENTS;
    }

    // --- pass 1: shapes (the charge track, the READY hairline) ---------------------------

    public void shapes(World w) {
        int[] paid = paidElements(w);
        Hangar h = w.hangar;
        boolean ready = h.forgeCharges[w.lokaIndex] > 0;
        boolean forged = h.isForged(w.lokaIndex);

        // The block grows UP from the bottom margin; rows stack above the margin.
        int rows = paid.length;
        float rowsTop = MARGIN + rows * ROW_H + (rows - 1) * ROW_GAP;

        // While the Astra winds up the whole track burns brighter as the charge builds, so the
        // corner you have been watching all run is the corner that tells you it is going off.
        float flashUp = w.winding() ? 1f + 0.8f * w.windK() : 1f;

        for (int row = 0; row < rows; row++) {
            int t = paid[row];
            float rowBottom = rowsTop - (row + 1) * ROW_H - row * ROW_GAP;
            float segY = rowBottom + (ROW_H - SEG_H) / 2f;
            float[] c = World.TATVA_RGB[t];
            float p = Math.min(SEGMENTS, progress(h, w, t));
            int whole = (int) p;

            // Every slot is drawn, not just the filled ones: without the empty ones a track
            // reads as a few floating dots instead of "3 of 5".
            for (int n = 0; n < SEGMENTS; n++) {
                float px = trackLeft() + n * (SEG_W + SEG_GAP);
                if (forged) {
                    // SOLID CELLS. A cell you are filling to release.
                    ui.shapes.setColor(c[0], c[1], c[2], 0.18f);
                    ui.shapes.rect(px, segY, SEG_W, SEG_H);
                } else {
                    // HOLLOW SOCKETS. An empty frame you are putting something INTO. Same five
                    // slots, same fill - but the shape says which of the two jobs you are doing.
                    ui.shapes.setColor(c[0], c[1], c[2], 0.5f);
                    ui.shapes.rect(px, segY, SEG_W, 1);
                    ui.shapes.rect(px, segY + SEG_H - 1, SEG_W, 1);
                    ui.shapes.rect(px, segY, 1, SEG_H);
                    ui.shapes.rect(px + SEG_W - 1, segY, 1, SEG_H);
                }
                float k = n < whole ? 1f : n == whole ? p - whole : 0f;
                if (k > 0) {
                    // the fill sits in the socket or the cell alike, rising from the floor
                    ui.shapes.setColor(c[0], c[1], c[2],
                            (forged ? 0.95f : 0.75f) * flashUp);
                    ui.shapes.rect(px, segY, SEG_W * k, SEG_H);
                }
            }

            // a release in hand: the gem gets a gold hairline. This is the only loud state,
            // and there is only one action behind it - F.
            if (ready) {
                float gx = rowLeft(), gy = rowBottom;
                ui.shapes.setColor(0.9f, 0.72f, 0.1f, 0.85f);
                ui.shapes.rect(gx - 2, gy - 2, GEM + 4, 1f);
                ui.shapes.rect(gx - 2, gy + GEM + 1, GEM + 4, 1f);
                ui.shapes.rect(gx - 2, gy - 2, 1f, GEM + 4);
                ui.shapes.rect(gx + GEM + 1, gy - 2, 1f, GEM + 4);
            }
        }
    }

    // --- pass 2: batch (gems, counts) ------------------------------------------------------

    public void draw(SpriteBatch batch, World w) {
        Hangar h = w.hangar;
        int[] paid = paidElements(w);
        int rows = paid.length;
        float rowsTop = MARGIN + rows * ROW_H + (rows - 1) * ROW_GAP;
        for (int row = 0; row < rows; row++) {
            int t = paid[row];
            float rowBottom = rowsTop - (row + 1) * ROW_H - row * ROW_GAP;
            boolean empty = h.shards[t] == 0;
            float k = 1f + flash(w, t) * 0.35f;     // the pickup pop
            float gx = rowLeft(), gy = rowBottom;
            // UNTINTED: the art's own colours, exactly as they are out in the world.
            batch.setColor(1, 1, 1, empty ? 0.5f : 1f);
            batch.draw(reg[t], gx + (GEM - GEM * k) / 2, gy + (GEM - GEM * k) / 2, GEM * k, GEM * k);
            batch.setColor(1, 1, 1, 1);

            // the count: right-aligned into its column, dim - it is the sell value
            String n = Integer.toString(h.shards[t]);
            ui.hud.setColor(1, 1, 1, empty ? UiTheme.A_HINT : UiTheme.A_LINE);
            ui.right(batch, ui.hud, n, gx - 8, gy + GEM / 2f + 5);
            ui.hud.setColor(1, 1, 1, 1);
        }
    }

    /** The charge track's left edge. The count and the gem key off the gem, so the row is one
     *  unit rather than three things that happen to be near each other. Public so a pixel test
     *  samples the real thing instead of recomputing this formula and drifting from it. */
    public static float trackLeft() { return rowLeft() - COUNT_W - 10 - TRACK_W; }

    /** The gem's left edge, hard against the right margin. */
    public static float rowLeft() { return Ui.UI_W - MARGIN - GEM; }

    /** Segment n's left edge. */
    public static float segLeft(int n) { return trackLeft() + n * (SEG_W + SEG_GAP); }

    /** The segment row's bottom edge, vertically centred in a row. */
    public static float segY(float rowBottom) { return rowBottom + (ROW_H - SEG_H) / 2f; }

    public void dispose() { for (Texture t : tex) t.dispose(); }
}