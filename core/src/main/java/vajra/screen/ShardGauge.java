package vajra.screen;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import vajra.ship.Hangar;
import vajra.world.LokaTable;
import vajra.world.World;

/** The Astra cells, in the armament console: one cell per Pancha Tatva THIS loka pays.
 *
 *  <p>Not five cells. Atala pays Akasha alone, so drawing the other four would be four dead
 *  cells and a lie about where the player's shards come from. The row grows only where the world
 *  does (Rasatala pays two elements and gets two cells, Patala all five), and
 *  {@code LokaTable.at(loka).shards} is the single source for which.
 *
 *  <p><b>A cell is the gem, the count, the track.</b> The gem is the element, drawn as the menus
 *  draw it. The count is how many shards you hold (they sell for coins, which is a real
 *  decision). The track is progress toward the next Astra release, in {@link #SEGMENTS} segments
 *  of one shard each, the segment in progress filling partially. Before the Astra is forged the
 *  segments are <b>hollow sockets</b> (frames you are gathering into); once forged they are
 *  <b>solid cells</b> filling toward a release. One weapon, so one track.
 *
 *  <p>A hairline round the gem means <b>a release is in hand: press F</b>, the one loud state.
 *  While the charge winds up the whole track burns brighter as it builds.
 *
 *  <p>Read-only. Two passes like the rest of the HUD: {@link #shapes} inside the shape pass,
 *  {@link #draw} inside the batch pass. The console decides where the cells go and passes the
 *  origin in. */
public final class ShardGauge {
    public static final float GEM = 30;
    public static final float SEG_W = 5, SEG_H = 5, SEG_GAP = 3;
    /** Five, because both FORGE_COST and CHARGE_COST are five: the same five segments read
     *  "shards to forge it" and "shards to the next release" with no label change. */
    public static final int SEGMENTS = 5;
    public static final float TRACK_W = SEGMENTS * SEG_W + (SEGMENTS - 1) * SEG_GAP;
    /** One cell: the gem, a gap, and the column the count and the track share. */
    public static final float COL_W = 42, CELL_W = GEM + 8 + COL_W, CELL_GAP = 16;

    /** Width of a row of `cells` cells. */
    public static float width(int cells) {
        return cells * CELL_W + Math.max(0, cells - 1) * CELL_GAP;
    }

    private final Ui ui;
    private final float[] popAt = new float[World.TATVA.length]; // world time of the last drop
    private final int[] lastCount = new int[World.TATVA.length];
    private static final float FLASH = 0.25f;                   // seconds

    public ShardGauge(Ui ui) {
        this.ui = ui;
        for (int i = 0; i < popAt.length; i++) popAt[i] = -999f;
    }

    /** The elements this run can actually be paid in: the loka's own, in pancha order. */
    public static int[] paidElements(World w) {
        return LokaTable.at(w.lokaIndex).shards;
    }

    /** Flash the cell whose count just went up. Watching the counts rather than being told keeps
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
     *  count beside it still read five: two numbers disagreeing about the same five shards.
     *  Selling shards is the only thing that pulls this back down, and it should.
     *
     *  <p>Every element the loka pays feeds the same forge, so a cell's own count is its share
     *  of the progress, which is why Rasatala's two cells both move. */
    public static float progress(Hangar h, World w, int t) {
        int need = h.forgeNeed(w.lokaIndex);
        // shards/need is the fraction of the COST, and SEGMENTS is the whole track: a full
        // cost is a full track, and because need == SEGMENTS each segment is one shard.
        return need <= 0 ? SEGMENTS : h.shards[t] / (float) need * SEGMENTS;
    }

    /** A cell's left edge, for the i-th of the loka's cells, in a row starting at x0. */
    public static float cellX(float x0, int i) {
        return x0 + i * (CELL_W + CELL_GAP);
    }

    /** The bottom edge of the track's segments, for a row of cells in the band y0..y0+h. */
    public static float segY(float y0, float h) {
        return y0 + h / 2f - 13 - SEG_H / 2f;
    }

    /** The track's left edge inside a cell. */
    public static float trackX(float cellX) {
        return cellX + GEM + 8;
    }

    // --- pass 1: shapes (the tracks, the READY hairline) ----------------------------------

    /** `x0` is the row's left edge, `y0` and `h` the band the cells are centred in. */
    public void shapes(World w, float x0, float y0, float h) {
        int[] paid = paidElements(w);
        Hangar h2 = w.hangar;
        boolean ready = h2.forgeCharges[w.lokaIndex] > 0;
        boolean forged = h2.isForged(w.lokaIndex);
        // While the Astra winds up the whole track burns brighter as the charge builds.
        float flashUp = w.winding() ? 1f + 0.8f * w.windK() : 1f;

        for (int i = 0; i < paid.length; i++) {
            int t = paid[i];
            float cx = cellX(x0, i), mid = y0 + h / 2f;
            float segY = mid - 13 - SEG_H / 2f;
            float[] c = World.TATVA_RGB[t];
            float p = Math.min(SEGMENTS, progress(h2, w, t));
            int whole = (int) p;
            for (int n = 0; n < SEGMENTS; n++) {
                float px = trackX(cx) + n * (SEG_W + SEG_GAP);
                if (forged) { // solid cells: a cell you are filling to release
                    ui.shapes.setColor(c[0], c[1], c[2], 0.20f);
                    ui.shapes.rect(px, segY, SEG_W, SEG_H);
                } else { // hollow sockets: an empty frame you are gathering into
                    ui.shapes.setColor(c[0], c[1], c[2], 0.55f);
                    ui.shapes.rect(px, segY, SEG_W, 1);
                    ui.shapes.rect(px, segY + SEG_H - 1, SEG_W, 1);
                    ui.shapes.rect(px, segY, 1, SEG_H);
                    ui.shapes.rect(px + SEG_W - 1, segY, 1, SEG_H);
                }
                float k = n < whole ? 1f : n == whole ? p - whole : 0f;
                if (k > 0) {
                    ui.shapes.setColor(c[0], c[1], c[2], (forged ? 0.95f : 0.8f) * Math.min(1f, flashUp));
                    ui.shapes.rect(px, segY, SEG_W * k, SEG_H);
                }
            }
            if (ready) { // a release in hand: the gem is ringed in ochre
                float gx = cx, gy = mid - GEM / 2f;
                Puranic.c(ui, Puranic.OCHRE, 0.7f + 0.3f * (float) Math.sin(Puranic.time() * 5f));
                ui.shapes.rect(gx - 2, gy - 2, GEM + 4, 1.5f);
                ui.shapes.rect(gx - 2, gy + GEM + 0.5f, GEM + 4, 1.5f);
                ui.shapes.rect(gx - 2, gy - 2, 1.5f, GEM + 4);
                ui.shapes.rect(gx + GEM + 0.5f, gy - 2, 1.5f, GEM + 4);
            }
        }
    }

    // --- pass 2: batch (gems, counts) -------------------------------------------------------

    public void draw(SpriteBatch batch, World w, float x0, float y0, float h) {
        Hangar h2 = w.hangar;
        int[] paid = paidElements(w);
        for (int i = 0; i < paid.length; i++) {
            int t = paid[i];
            boolean empty = h2.shards[t] == 0;
            float k = 1f + flash(w, t) * 0.35f; // the pickup pop
            float cx = cellX(x0, i), mid = y0 + h / 2f, gy = mid - GEM / 2f;
            batch.setColor(1, 1, 1, empty ? 0.55f : 1f);
            batch.draw(ui.icons.shardUi(t), cx + (GEM - GEM * k) / 2, gy + (GEM - GEM * k) / 2, GEM * k, GEM * k);
            batch.setColor(1, 1, 1, 1);
            ui.num.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], empty ? 0.5f : 1f);
            ui.num.draw(batch, Integer.toString(h2.shards[t]), trackX(cx), mid + 6 + ui.num.getCapHeight());
            ui.num.setColor(1, 1, 1, 1);
        }
    }

    public void dispose() { }
}
