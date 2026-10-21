package vajra.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.ship.ShipControls;

/** THE PHASE-2 GATE: two worlds grown from one seed, flown by one scripted pilot, must be
 *  THE SAME RUN — score, kills, position, orb spots, minute for minute. A seeded shadow
 *  race lives or dies here: any drift between two identical drives kills the scoreboard's
 *  meaning. The script is the sim's whole input (ShipControls), so no GL is involved. */
class DeterminismTest {
    private static final float STEP = 1 / 60f;

    /** A deterministic pilot: a fixed weave-and-fire pattern driven by the step counter. */
    static class Script implements ShipControls {
        int step;

        @Override public float thrustY() { return 1; } // always climbing
        @Override public float thrustX() {
            return (step / 40) % 2 == 0 ? 1 : -1;      // weave right, weave left
        }
        @Override public float rotate() { return (step / 90) % 2 == 0 ? 1 : -1; }
        @Override public boolean fire() { return step % 7 != 0; } // near-constant fire
        @Override public boolean boost() { return (step / 120) % 2 == 0; } // boost in bursts
        @Override public boolean dash() { return step % 180 < 6; } // dash pulses
        @Override public boolean shield() { return false; }
        @Override public boolean mouseAim() { return false; }
        @Override public float aimX() { return 0; }
        @Override public float aimY() { return 0; }
    }

    @Test
    void twoSeededWorldsFlyTheSameRun() {
        World a = new World(4242);
        World b = new World(4242);
        assertSameRun(a, b, "fresh worlds");

        Script in = new Script();
        for (int i = 0; i < 3600; i++) { // 60 sim-seconds of climbing and fighting
            in.step = i;
            a.update(STEP, in);
            in.step = i; // each world gets THE SAME step's inputs
            b.update(STEP, in);
            if (a.runEnded() || b.runEnded()) break; // a dead pilot freezes both, identically
        }
        assertSameRun(a, b, "after 60 flight-seconds");
    }

    private static void assertSameRun(World a, World b, String when) {
        assertEquals(a.score.points, b.score.points, "score " + when);
        assertArrayEquals(a.kills, b.kills, "kills " + when);
        assertEquals(a.ship.x, b.ship.x, 0f, "ship x " + when);
        assertEquals(a.ship.y, b.ship.y, 0f, "ship y " + when);
        for (int i = 0; i < a.orbs.length; i++) {
            assertEquals(a.orbs[i].x, b.orbs[i].x, 0f, "orb " + i + " x " + when);
            assertEquals(a.orbs[i].y, b.orbs[i].y, 0f, "orb " + i + " y " + when);
        }
        assertEquals(a.lokaIndex, b.lokaIndex, "loka " + when);
        assertEquals(a.boss == null, b.boss == null, "boss presence " + when);
    }

    @Test
    void aDifferentSeedGrowsADifferentChaos() {
        World one = new World(1);
        World two = new World(2);
        boolean anyDifferent = false;
        for (int i = 0; i < one.orbs.length; i++) {
            if (one.orbs[i].x != two.orbs[i].x || one.orbs[i].y != two.orbs[i].y) anyDifferent = true;
        }
        assertTrue(anyDifferent, "seeds 1 and 2 must not place the orbs alike");
    }

    @Test
    void seedZeroIsTheLegacyWorld() {
        // the constants every existing test was pinned against are untouched at seed 0
        World legacy = new World();
        World explicit = new World(0);
        assertSameRun(legacy, explicit, "both zero-seed paths");
    }
}
