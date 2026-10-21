package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.world.World;

/** The loka indicator's scroll math — pure altitude, so it is testable without a GL context. */
class HudTest {
    @Test
    void theTapeFallsAsYouClimbAndRisesAsYouDrop() {
        float rest = Hud.COL_TOP - Hud.pipY(4, 500);
        float climb = Hud.COL_TOP - Hud.pipY(4, 550);
        assertTrue(climb < rest, "climbing drops the pips down the screen: " + rest + " -> " + climb);
        float drop = Hud.COL_TOP - Hud.pipY(4, 450);
        assertTrue(rest < drop, "dropping lifts them back up: " + rest + " -> " + drop);
        for (float y = 0; y < 20000; y += 137) {
            float v = Hud.pipY(7, y);
            assertTrue(v >= 0 && v < Hud.COL_H, "the tape stays in its column: " + v);
        }
    }

    @Test
    void theMeterStartsAtTheAtalaGate() {
        assertEquals(0, Hud.yojanaAt(World.ATALA_Y), "0 at the grand gate");
        assertEquals(10000, Hud.yojanaAt(20600), "the ceiling is Atala's canonical 10,000");
        assertEquals(0, Hud.yojanaAt(800), "Bhu Loka clamps to 0 (the tape stays hidden)");
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
