package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.world.World;

/** The HUD's pure maths: the yojana reading, the route ruler and the results card. No GL needed. */
class HudTest {
    /** The route is a ruler of the whole climb: higher in the world is higher on the ruler, and
     *  nothing can land off it. */
    @Test
    void theRouteRisesWithTheClimbAndStaysOnTheRuler() {
        World w = new World();
        float wh = w.regionEnd[w.regionEnd.length - 1];
        float prev = -1;
        for (float y = 0; y <= wh; y += 250) {
            float v = HudRoute.routeY(y, wh);
            assertTrue(v >= prev, "climbing never moves the ship down the ruler");
            assertTrue(v >= HudRoute.RBOT && v <= HudRoute.RTOP, "it stays on the ruler: " + v);
            prev = v;
        }
        assertEquals(HudRoute.RBOT, HudRoute.routeY(-500, wh), 0.001f, "below the floor clamps to the bottom");
        assertEquals(HudRoute.RTOP, HudRoute.routeY(wh + 500, wh), 0.001f, "above the ceiling clamps to the top");
    }

    /** Every gate, shop and checkpoint the route draws must fall inside the ruler. */
    @Test
    void everyPointOfInterestLandsOnTheRuler() {
        World w = new World();
        float wh = w.regionEnd[w.regionEnd.length - 1];
        for (float s : w.regionStart) assertTrue(HudRoute.routeY(s, wh) >= HudRoute.RBOT);
        for (float[] p : w.shops) {
            float v = HudRoute.routeY(p[1], wh);
            assertTrue(v >= HudRoute.RBOT && v <= HudRoute.RTOP, "shop at " + p[1]);
        }
        for (vajra.world.Checkpoint c : w.checkpoints)
            if (c != null) {
                float v = HudRoute.routeY(c.y, wh);
                assertTrue(v >= HudRoute.RBOT && v <= HudRoute.RTOP, "checkpoint at " + c.y);
            }
    }

    @Test
    void theMeterStartsAtTheAtalaGate() {
        assertEquals(0, Hud.yojanaAt(World.ATALA_Y), "0 at the grand gate");
        assertEquals(10000, Hud.yojanaAt(20600), "the ceiling is Atala's canonical 10,000");
        assertEquals(0, Hud.yojanaAt(800), "Bhu Loka clamps to 0 (the tag shows no reading)");
    }

    /** The results card reads the finished run back: score against the best, purse, clock. */
    @Test
    void theResultsCardReadsTheRunBack() {
        World w = new World();
        w.score.add(1234);
        w.hangar.best = 1234;
        w.hangar.coins = 56;
        for (int i = 0; i < 3; i++) w.kills[i] = 2;
        String[] lines = Hud.resultsLines(w);
        assertTrue(lines[0].contains("SCORE " + w.score.digits()), lines[0]);
        assertTrue(lines[0].contains("NEW"), "tying the best reads as a new record: " + lines[0]);
        assertTrue(lines[1].contains("KILLS 6"), lines[1]);
        assertTrue(lines[1].contains("COINS 56"), lines[1]);
        assertTrue(lines[2].startsWith("TIME 0:00"), lines[2]);

        w.hangar.best = 999999;
        lines = Hud.resultsLines(w);
        assertFalse(lines[0].contains("NEW"), "a run under the best is just the best: " + lines[0]);
    }
}
