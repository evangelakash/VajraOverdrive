package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.ship.Hangar;
import vajra.world.LokaTable;
import vajra.world.World;

/** The Astra cells in the armament console.
 *
 *  <p>The one rule that matters is the first: it draws a cell per element the LOKA pays, not one
 *  per Pancha Tatva. Atala pays Akasha alone, so four of the five cells would be dead and a
 *  lie about where the player's shards come from. */
class ShardGaugeTest {

    @Test
    void atalaShowsExactlyOneRowBecauseItPaysOneElement() {
        World w = new World();
        assertEquals(0, w.lokaIndex, "a fresh run starts in Atala");
        int[] paid = ShardGauge.paidElements(w);
        assertEquals(1, paid.length, "Atala pays one element, so the block is one row");
        assertEquals(Hangar.AKASHA, paid[0], "and it is Akasha");
    }

    /** The block grows with the loka rather than being fixed at one row. */
    @Test
    void deeperLokasGetMoreRows() {
        assertEquals(2, LokaTable.at(5).shards.length, "Rasatala pays two");
        assertEquals(5, LokaTable.at(6).shards.length, "Patala pays all five");
        for (int l = 0; l < LokaTable.COUNT; l++)
            assertTrue(LokaTable.at(l).shards.length >= 1,
                    "loka " + l + " pays at least one element");
    }

    /** Every element the block can draw must have art and a colour, or a row would be blank. */
    @Test
    void everyPaidElementHasAColourAndAnAsset() {
        assertEquals(World.TATVA.length, World.TATVA_RGB.length, "a colour per element");
        for (int l = 0; l < LokaTable.COUNT; l++)
            for (int t : LokaTable.at(l).shards) {
                assertTrue(t >= 0 && t < World.TATVA.length,
                        "loka " + l + " names a real element: " + t);
                float[] c = World.TATVA_RGB[t];
                assertTrue(c[0] > 0f && c[1] > 0f && c[2] > 0f,
                        World.TATVA[t] + " has a lit colour");
            }
    }

    /** The cells sit in the armament console: a row of one cell is narrow, five are wide, and the
     *  widest console still fits between the radar dial and the right edge. */
    @Test
    void theCellsFitTheConsole() {
        assertTrue(ShardGauge.width(1) < ShardGauge.width(5), "the row grows with the loka");
        World w = new World();
        w.lokaIndex = 6; // Patala pays all five: the widest the console gets
        float x0 = Hud.consoleX(w), x1 = x0 + Hud.consoleW(w);
        float dialRight = HudCompass.CX + HudCompass.R + 14;
        assertTrue(x0 > dialRight, "the widest console (x " + (int) x0 + ") clears the radar dial (to x "
                + (int) dialRight + ")");
        assertTrue(x1 < Ui.UI_W - dialRight, "and the right side is as clear as the left (" + (int) x1 + ")");
    }

    /** And it must not run off the left edge at its widest. */
    @Test
    void theBlockFitsTheScreen() {
        // the track is five segments and their gaps - one segment per shard of the next cost
        assertEquals(ShardGauge.SEGMENTS * ShardGauge.SEG_W
                        + (ShardGauge.SEGMENTS - 1) * ShardGauge.SEG_GAP,
                ShardGauge.TRACK_W, 0.01f, "one segment per shard");
        assertEquals(Hangar.CHARGE_COST, ShardGauge.SEGMENTS,
                "the track is exactly as wide as a release costs, so the count reads off it");
    }

    /** The track is progress toward the next Astra release, and it reads the loka's OWN
     *  element only - the purity rule the forge already obeys. */
    @Test
    void theTrackReadsForgeProgress() {
        World w = new World();
        Hangar h = w.hangar;          // the world's own hangar: hangar is final
        int t = Hangar.AKASHA;
        assertEquals(0f, ShardGauge.progress(h, w, t), 0.001f, "nothing banked");

        // in SEGMENT units, and SEGMENTS == CHARGE_COST: so one segment is one shard, and
        // three of five fills three segments. Fed through addShard, the only route a pickup takes.
        for (int i = 0; i < 3; i++) h.addShard(t, 0);
        assertEquals(3f, ShardGauge.progress(h, w, t), 0.001f, "three of five");
        assertFalse(h.isForged(0), "not forged at three");

        for (int i = 0; i < 2; i++) h.addShard(t, 0);
        assertTrue(h.isForged(0), "the fifth forges it");
        assertEquals(1, h.forgeCharges[0], "forging hands you the first release");
        // The track reads the shard PILE, and forging never spends shards - it banks them.
        // So a full track after forging is correct: it means "this pile is worth a release",
        // and the next five carry on from there.
        assertEquals(ShardGauge.SEGMENTS, ShardGauge.progress(h, w, t), 0.001f,
                "the pile is intact: forging banks, it does not spend");
        h.sellShards();                       // selling DOES spend them
        assertEquals(0f, ShardGauge.progress(h, w, t), 0.001f,
                "and selling pulls the track back down, which is the only way to lose ground");
    }

    /** Only the elements the loka pays ever get a row, so Atala's block is one row and Patala's
     *  is five. Drawing the other four in Atala would be four dead lines and a lie. */
    @Test
    void onlyPaidElementsGetARow() {
        World w = new World();
        w.lokaIndex = 0;                      // Atala
        assertEquals(1, ShardGauge.paidElements(w).length);
        w.lokaIndex = 6;                      // Patala pays all five
        assertEquals(5, ShardGauge.paidElements(w).length);
    }
}
