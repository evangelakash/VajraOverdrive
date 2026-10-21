package vajra.ship;

import vajra.world.Effect;
import vajra.world.World;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ShipTest {
    private static ShipControls controls(float tx, float ty, float rot, boolean fire, boolean boost, boolean dash, boolean shield) {
        return new ShipControls() {
            public float thrustX() { return tx; }
            public float thrustY() { return ty; }
            public float rotate() { return rot; }
            public boolean fire() { return fire; }
            public boolean boost() { return boost; }
            public boolean dash() { return dash; }
            public boolean shield() { return shield; }
            public boolean mouseAim() { return false; }
            public float aimX() { return 0; }
            public float aimY() { return 0; }
        };
    }

    private static final ShipControls NONE = controls(0, 0, 0, false, false, false, false);

    @Test
    void thrustIsScreenRelative() {
        World w = new World();
        w.ship.facing = 0; // nose east: the old nose-relative scheme sent W east too
        w.ship.update(0.016f, controls(0, 1, 0, false, false, false, false), w);
        assertTrue(w.ship.vy > 0, "W is north, whatever the nose is doing");
        assertEquals(0f, w.ship.vx, 0.001f, "and nothing sideways");
    }

    @Test
    void strafeIsScreenRelative() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.update(0.016f, controls(1, 0, 0, false, false, false, false), w);
        assertTrue(w.ship.vx > 0, "D is east");
        assertEquals(0f, w.ship.vy, 0.001f);
    }

    /** Plume time banks only while a key is down, and letting go drops it back to zero. */
    @Test
    void thrustHoldAccumulatesWhileHeldAndResets() {
        World w = new World();
        ShipControls up = controls(0, 1, 0, false, false, false, false);
        for (int i = 0; i < 25; i++) w.ship.update(0.016f, up, w);
        assertEquals(0.4f, w.ship.thrustHold, 0.02f, "held keys bank plume time");
        for (int i = 0; i < 5; i++) w.ship.update(0.016f, NONE, w);
        assertEquals(0f, w.ship.thrustHold, 0.001f, "letting go restarts the ramp");
    }

    /** WASD is pure translation: pushing must never swing the nose. */
    @Test
    void wasdNeverTurnsTheNose() {
        World w = new World();
        w.ship.facing = 1.2f;
        for (int i = 0; i < 90; i++) w.ship.update(0.016f, controls(1, 0, 0, false, false, false, false), w);
        assertEquals(1.2f, w.ship.facing, 0.01f, "pushing right must not turn the ship");
        assertTrue(w.ship.vx > 0, "but it still pushes");
    }

    @Test
    void noseHoldsHeadingWhenParked() {
        World w = new World();
        w.ship.facing = 1.2f;
        for (int i = 0; i < 30; i++) w.ship.update(0.016f, NONE, w);
        assertEquals(1.2f, w.ship.facing, 0.01f, "no drift while sitting still");
    }

    @Test
    void qTurnsTheNoseCounterClockwise() {
        World w = new World();
        for (int i = 0; i < 30; i++) w.ship.update(0.016f, controls(1, 0, 1, false, false, false, false), w);
        assertTrue(w.ship.facing > 0, "Q turns left even while pushing, facing " + w.ship.facing);
    }

    @Test
    void rotateTurnsTheNose() {
        World w = new World();
        float f0 = w.ship.facing;
        w.ship.update(0.5f, controls(0, 0, -1, false, false, false, false), w);
        assertTrue(w.ship.facing < f0, "E turns right");
    }

    @Test
    void dashGrantsIFramesThenCoolsDown() {
        World w = new World();
        Ship s = w.ship;
        ShipControls push = controls(0, 1, 0, false, false, true, false);
        s.update(0.016f, push, w);
        assertTrue(s.iFrames > 0, "dash gives i-frames");
        assertEquals(0f, s.dashCd, 0.001f,
                "the wait starts at the TAIL of the smear — the burst is movement, not dead time");
        assertTrue(Math.sqrt(s.vx * s.vx + s.vy * s.vy) > 900,
                "no ease-in: the burst opens at full speed");
        float peak = 0, last = 0;
        for (int i = 0; i < 22; i++) {
            s.update(0.016f, push, w);
            float sp = (float) Math.sqrt(s.vx * s.vx + s.vy * s.vy);
            if (sp > peak) peak = sp;
            last = sp;
        }
        assertTrue(peak > 900, "dash is a swift burst, got " + peak);
        assertTrue(last < peak / 2, "and settles back down, got " + last);
        assertTrue(s.dashCd > 0, "holding C must not re-dash");
        for (int i = 0; i < 100; i++) s.update(0.016f, NONE, w);
        assertTrue(s.dashCd <= 0, "cooldown expires, was " + s.dashCd);
        s.update(0.016f, controls(0, 1, 0, false, false, false, false), w); // release frame
        s.update(0.016f, controls(0, 1, 0, false, false, true, false), w);
        assertTrue(s.iFrames > 0, "can dash again after cooldown");
    }

    /** Dash is a ghost move: it must not throw a particle trail behind it. */
    @Test
    void dashLeavesNoParticles() {
        World w = new World();
        ShipControls dash = controls(1, 0, 0, false, false, true, false);
        w.ship.update(0.016f, dash, w);
        for (int i = 0; i < 5; i++) w.ship.update(0.016f, dash, w);
        assertTrue(w.ship.dashTime > 0, "the dash is still running");
        assertTrue(w.effects.isEmpty(), "and left nothing behind");
    }

    /** Thrusting drops a short exhaust trail; a dying hull trails smoke. Both always leave from
     *  behind the hull — the nose points north here, so the sparks must appear below the ship. */
    @Test
    void exhaustAndSmokeSparks() {
        World w = new World();
        w.ship.update(0.016f, controls(1, 0, 0, false, false, false, false), w);
        assertFalse(w.effects.isEmpty(), "the drive sparks while it pushes");
        assertTrue(w.effects.get(0).y < w.ship.y, "exhaust leaves from behind, never the nose");
        w.effects.clear();
        w.ship.hp = w.ship.maxHp() * 0.2f;
        for (int i = 0; i < 5; i++) w.ship.update(0.016f, NONE, w);
        assertFalse(w.effects.isEmpty(), "black puffs trail the wounded hull");
        assertEquals(Effect.SMOKE, w.effects.get(0).type, "the 4-frame sheet, not a shape dot");
        assertTrue(w.effects.get(0).y < w.ship.y, "smoke too — rear of the hull, always");
    }

    /** The deform follows the push: a dash remembers which way it went. */
    @Test
    void dashStoresItsDirection() {
        World w = new World();
        Ship s = w.ship;
        s.update(0.016f, controls(1, 0, 0, false, false, true, false), w); // push east
        assertEquals(1f, s.dashDirX, 0.01f, "east push remembers east");
        assertEquals(0f, s.dashDirY, 0.01f, "no phantom vertical");
        for (int i = 0; i < 100; i++) s.update(0.016f, NONE, w); // wait out any cooldown
        s.facing = (float) Math.PI / 2; // nose north
        s.update(0.016f, controls(0, 0, 0, false, false, true, false), w); // bare burst
        assertEquals(0f, s.dashDirX, 0.01f, "no keys: burst goes along the nose");
        assertEquals(1f, s.dashDirY, 0.01f, "which is north");
    }

    @Test
    void boostDrainsAndRegenerates() {
        World w = new World();
        Ship s = w.ship;
        s.update(0.5f, controls(0, 1, 0, false, true, false, false), w);
        assertTrue(s.boosting, "boosting while held with energy");
        assertTrue(s.boostEnergy < 100, "boost drains energy");
        float afterUse = s.boostEnergy;
        s.update(0.016f, NONE, w);
        assertTrue(s.boostEnergy > afterUse, "and flows back when you let go");
        for (int i = 0; i < 60; i++) s.update(0.016f, NONE, w); // 1s
        assertTrue(s.boostEnergy < 100, "a second of coasting is not a refill");
        for (int i = 0; i < 500; i++) s.update(0.016f, NONE, w); // ~9s total
        assertEquals(100f, s.boostEnergy, 0.01f, "but ~14s empties back to full, not half a minute");
    }

    /** Boost has to be worth spending: four-ish seconds of it off a full bar. */
    @Test
    void boostLastsLongEnoughToMatter() {
        World w = new World();
        Ship s = w.ship;
        ShipControls push = controls(0, 1, 0, false, true, false, false);
        s.update(0.016f, push, w);
        float t = 0;
        while (s.boosting && t < 20) {
            s.update(0.016f, push, w);
            t += 0.016f;
        }
        assertTrue(t > 3f && t < 6f, "~4s of boost off a full bar, got " + t);
    }

    /** The shield prices what actually lands: a betray ring is cheap, a Bhavi shell is not. */
    @Test
    void shieldCostScalesWithTheHit() {
        World w = new World();
        Ship s = w.ship;
        ShipControls guard = controls(0, 0, 0, false, false, false, true);
        s.update(0.016f, guard, w);
        float full = s.shieldEnergy;
        s.damage(5, w);                       // a Hataka ring pellet
        float cheap = full - s.shieldEnergy;
        s.invuln = 0;
        s.update(0.016f, guard, w);
        s.shieldEnergy = full;
        s.damage(14, w);                      // a Bhavi shell
        float dear = full - s.shieldEnergy;
        assertEquals(5f, cheap, 0.01f);
        assertEquals(14f, dear, 0.01f);
        assertTrue(dear > cheap * 2, "a heavy shell must cost materially more than a pellet");
        assertEquals(0f, s.shieldEnergy - (full - dear), 0.01f, "hull untouched: the shield ate it");
        assertEquals(s.maxHp(), s.hp, 0.01f);
    }

    /** The dip replaces the halo: the hull sinks on a hit and eases back up. */
    @Test
    void aHitPressesTheHullDownAndItComesBackUp() {
        World w = new World();
        Ship s = w.ship;
        assertEquals(0f, s.dip, 0.001f, "at rest the hull sits on its pivot");
        s.damage(10, w);
        for (int i = 0; i < 8; i++) s.update(0.016f, NONE, w);
        assertTrue(s.dip > 0.5f, "a hit presses it down, got " + s.dip);
        for (int i = 0; i < 120; i++) s.update(0.016f, NONE, w);
        assertTrue(s.dip < 0.05f, "and it eases back up, got " + s.dip);
    }

    @Test
    void boostStopsWhenEnergyRunsOut() {
        World w = new World();
        Ship s = w.ship;
        s.boostEnergy = 1;
        boolean everBoosted = false;
        for (int i = 0; i < 10; i++) {
            s.update(0.016f, controls(0, 1, 0, false, true, false, false), w);
            if (s.boosting) everBoosted = true;
            else break;
        }
        assertTrue(everBoosted, "boost ran while energy lasted");
        assertFalse(s.boosting, "boost stops when energy hits zero");
    }

    @Test
    void damageAppliesInvulnerability() {
        World w = new World();
        Ship s = w.ship;
        float hp0 = s.hp;
        s.damage(15, w);
        assertEquals(hp0 - 15, s.hp, 0.001f);
        assertTrue(s.invuln > 0);
        s.damage(15, w);
        assertEquals(hp0 - 15, s.hp, 0.001f, "invuln blocks the second hit");
    }

    @Test
    void shieldAbsorbsHits() {
        World w = new World();
        Ship s = w.ship;
        float hp0 = s.hp;
        float sh0 = s.shieldEnergy;
        s.shieldOn = true;
        s.damage(15, w);
        assertEquals(hp0, s.hp, 0.001f, "shield blocks hull damage");
        assertTrue(s.shieldEnergy < sh0, "shield drains instead");
        assertFalse(w.effects.isEmpty(), "and the bubble ripples where it was hit");
    }

    /** The push ramps in and coasts out — taps and direction flips glide instead of lurching. */
    @Test
    void thrustEasesInAndOut() {
        World w = new World();
        Ship s = w.ship;
        ShipControls push = controls(1, 0, 0, false, false, false, false);
        s.update(0.016f, push, w);
        float first = s.vx;
        assertTrue(first > 0, "the ramp starts moving at once");
        assertTrue(first < s.accel() * 0.016f * 0.5f, "but well under a raw frame's shove: " + first);
        for (int i = 0; i < 60; i++) s.update(0.016f, push, w);
        assertTrue(s.vx > first * 5, "builds to a full push, got " + s.vx);
        float held = s.vx;
        s.update(0.016f, NONE, w);
        assertTrue(s.vx < held, "and starts coasting down at once");
        assertTrue(s.vx > 0, "never an instant stop");
    }

    /** The SPD stat is the real number: thrust converges on maxSpeed(), not a drag constant. */
    @Test
    void thrustConvergesToMaxSpeed() {
        World w = new World();
        Ship s = w.ship;
        ShipControls push = controls(1, 0, 0, false, false, false, false);
        for (int i = 0; i < 300; i++) s.update(0.016f, push, w); // 4.8 s: far past settling
        float sp = (float) Math.hypot(s.vx, s.vy);
        assertEquals(s.maxSpeed(), sp, 1f, "cruise matches the SPD figure, got " + sp);
    }

    /** The nose glides onto the cursor instead of snapping to it. */
    @Test
    void noseGlidesToTheCursor() {
        World w = new World();
        Ship s = w.ship;
        s.x = 500;
        s.y = 500;
        s.facing = (float) (Math.PI / 2); // north; the cursor sits dead east
        ShipControls cursorEast = new ShipControls() {
            public float thrustX() { return 0; }
            public float thrustY() { return 0; }
            public float rotate() { return 0; }
            public boolean fire() { return false; }
            public boolean boost() { return false; }
            public boolean dash() { return false; }
            public boolean shield() { return false; }
            public boolean mouseAim() { return true; }
            public float aimX() { return 600; }
            public float aimY() { return 500; }
        };
        s.update(0.016f, cursorEast, w);
        assertTrue(s.facing > 0.01f, "one frame: turned toward it, not snapped to it");
        for (int i = 0; i < 100; i++) s.update(0.016f, cursorEast, w);
        assertEquals(0f, s.facing, 0.05f, "and settles on the cursor");
    }

    @Test
    void deathRespawnsAtRegionStart() {
        World w = new World();
        Ship s = w.ship;
        s.hp = 1;
        s.damage(999, w);
        assertEquals(s.maxHp(), s.hp, 0.001f, "respawn with full hull");
        assertEquals(120f, s.y, 1f, "respawn at the region's entrance");
    }
}
