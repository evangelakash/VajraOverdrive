package vajra.enemy;

import vajra.world.World;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Bala: the silent seal (no boss in his arena and no numbers on screen until the quota fills),
 *  wake rule, retinue ring, phase beats, blinks, stand-down and victory. */
class BossTest {
    /** Full world, ship parked in the arena past the wake line. */
    private World arena() {
        World w = new World();
        w.ship.x = 2600;
        w.ship.y = 19600;
        return w;
    }

    private void meetQuota(World w) {
        for (int i = 0; i < w.quota.length; i++) w.kills[i] = w.quota[i];
    }

    private Enemy bossOf(World w) {
        for (Enemy e : w.enemies) if (e.kind == Enemy.Kind.BALA) return e;
        return fail("no Bala in the roster");
    }

    /** Lift the seal the way the run would: quota full, then he is simply there. */
    private Enemy bossIn(World w) {
        meetQuota(w);
        w.trySpawnBoss();
        return bossOf(w);
    }

    @Test
    void quotasAreObtainableFromTheRosterAndStartHidden() {
        World w = new World();
        assertFalse(w.quotaMet(), "a fresh run starts sealed");
        assertNull(w.boss, "no Bala exists anywhere yet");
        int[] placed = new int[Enemy.Kind.values().length];
        for (Enemy o : w.enemies) placed[o.kind.ordinal()]++;
        for (Enemy.Kind k : Enemy.Kind.values()) {
            assertTrue(w.quota[k.ordinal()] <= placed[k.ordinal()],
                    k + " quota must fit the roster");
            if (k != Enemy.Kind.BALA)
                assertTrue(w.quota[k.ordinal()] >= 3, k + " quota too soft to matter");
        }
    }

    @Test
    void doesNotAppearUntilTheQuotaIsFull() {
        World w = arena();
        w.trySpawnBoss();
        assertNull(w.boss, "the arena holds no Bala while the quota is open");
        int band = 0;
        for (Enemy o : w.enemies) // his band is still there: this is the fight you must have
            if (!o.dead && o.homeY > 17600) band++;
        assertTrue(band >= 8, "the arena's own units stand in his place, got " + band);
        for (Enemy.Kind k : Enemy.Kind.values())
            if (k != Enemy.Kind.MAYAVI) w.kills[k.ordinal()] = w.quota[k.ordinal()];
        w.trySpawnBoss();
        assertNull(w.boss, "still one short");
        w.kills[Enemy.Kind.MAYAVI.ordinal()] = w.quota[Enemy.Kind.MAYAVI.ordinal()];
        w.trySpawnBoss();
        assertNotNull(w.boss, "the last kill brings him in");
    }

    @Test
    void spawnsOnceInPitWithCommanderStats() {
        World w = arena();
        Enemy b = bossIn(w);
        int n = 0;
        for (Enemy e : w.enemies) if (e.kind == Enemy.Kind.BALA) n++;
        assertEquals(1, n, "exactly one Bala");
        assertTrue(b.boss && b.rank == 1, "flagged boss, outranks everyone");
        assertEquals(96, b.w, "96 px hull");
        assertEquals(1500, b.hp, "1500 hp");
        assertEquals(2600, b.x, 0.5f, "parked at the pit centre");
        assertEquals(19104, b.y, 0.5f, "parked at the pit centre");
    }

    @Test
    void dormantUntilCrossingThenOrdersRetinue() {
        World w = new World();
        w.ship.x = 2600;
        w.ship.y = 5000; // far below the wake line
        Enemy b = bossIn(w);
        b.update(1 / 60f, w, 0f);
        assertFalse(b.active, "he holds until you cross the gate");
        w.ship.y = 18500;
        b.update(1 / 60f, w, 0f);
        assertTrue(b.active, "the crossing wakes him");
        int ring = 0;
        for (Enemy o : w.enemies) if (!o.dead && o.commander == b) ring++;
        assertTrue(ring >= 8, "the retinue forms the ring, got " + ring);
    }

    @Test
    void phaseBeatsStandDownAndBerserk() {
        World w = arena();
        Enemy b = bossIn(w);
        b.update(1 / 60f, w, 0f); // ship past the line: wakes + orders
        int ring = 0;
        for (Enemy o : w.enemies) if (!o.dead && o.commander == b) ring++;
        assertTrue(ring >= 1, "wake ordered someone");

        b.hp = b.maxHp * 0.5f; // under 2/3
        int barks = w.popups.activeCount();
        b.update(1 / 60f, w, 0f);
        assertEquals(2, b.bossPhase, "crossing 2/3 enters the duel");
        assertEquals(barks + 1, w.popups.activeCount(), "the duel gets his line");
        assertTrue(b.bossInvuln, "stagger beats eat hits");
        assertFalse(b.hurt(10, w), "stagger ignores damage");
        int standing = 0;
        for (Enemy o : w.enemies)
            if (!o.dead && !o.dismissed && o.commander == b) standing++;
        assertEquals(0, standing, "the duel stands everyone down");

        b.hp = b.maxHp * 0.25f; // under 1/3
        b.update(1 / 60f, w, 0f);
        assertEquals(3, b.bossPhase, "crossing 1/3 goes berserk");
        assertEquals(barks + 2, w.popups.activeCount(), "the berserk gets his line");
        assertTrue(b.bossBlink > 0, "the first blink is scheduled");
    }

    @Test
    void blinkDropsElitePairAndReappearsNearby() {
        World w = arena();
        Enemy b = bossIn(w);
        b.active = true;
        b.bossPhase = 3;
        b.bossBlink = 0;
        b.update(1 / 60f, w, 0f); // vanish
        assertTrue(b.bossHidden, "mid-blink he is nowhere");
        assertFalse(b.hurt(10, w), "mid-blink he cannot be hit");
        int elites = 0;
        for (Enemy o : w.enemies) if (!o.dead && o.elite && o.commander == b) elites++;
        assertEquals(2, elites, "an elite pair guards the old post");

        b.update(0.8f, w, 0f); // blink out
        assertFalse(b.bossHidden, "he reappears");
        float d = (float) Math.hypot(b.x - w.ship.x, b.y - w.ship.y);
        assertTrue(d >= 300 && d <= 900, "reappears 350-850 px out, got " + d);
        assertTrue(b.bossBlink > 3, "the next blink is scheduled");
    }

    @Test
    void elitePairsCapAtSix() {
        World w = arena();
        Enemy b = bossIn(w);
        b.active = true;
        for (int i = 0; i < 4; i++) { // four vanishes = eight pairs-worth of attempts
            b.bossHidden = false;
            b.bossPhase = 3;
            b.bossBlink = 0;
            b.update(1 / 60f, w, 0f);  // vanish
            b.update(0.8f, w, 0f);      // reappear
        }
        int elites = 0;
        for (Enemy o : w.enemies) if (!o.dead && o.elite && o.commander == b) elites++;
        assertEquals(6, elites, "the ring never bloats past six elites, got " + elites);
    }

    @Test
    void killCrownsVictoryAndPurses150() {
        World w = arena();
        Enemy b = bossIn(w);
        Enemy f = null;
        for (Enemy o : w.enemies) if (!o.dead && o.homeY > 17600 && o != b) { f = o; break; }
        assertNotNull(f, "someone in the arena");
        f.commander = b;
        int coins = w.hangar.coins;
        assertTrue(b.hurt(99999, w), "the kill lands");
        assertTrue(b.dead && w.bossDead, "Bala falls");
        assertEquals(0f, w.victoryT, "the victory clock starts");
        assertEquals(coins + 150, w.hangar.coins, "the kill purse");
        assertTrue(f.dismissed && f.commander == null, "the retinue stands down for good");
    }

    /** The quota is silent and the seal lifts once: the announcement is the run's own warden
     *  cutscene now (CutsceneScripts + GameScreen.storyBeats), not a floating banner. */
    @Test
    void theSealLiftsExactlyOnce() {
        World w = new World();
        meetQuota(w);
        w.trySpawnBoss();
        assertNotNull(w.boss, "the seal lifts: he is in the world");

        int live = w.popups.activeCount();
        w.trySpawnBoss(); // no second boss, no second call
        assertEquals(live, w.popups.activeCount(), "the seal lifts once");
        int n = 0;
        for (Enemy e : w.enemies) if (e.kind == Enemy.Kind.BALA) n++;
        assertEquals(1, n, "one Bala");
    }

    @Test
    void theKillEndsTheRunInVictory() {
        World w = arena();
        Enemy b = bossIn(w);
        b.hurt(99999, w);
        assertTrue(w.won, "Bala's fall ends the run — the arena is no longer a place to be");
        assertEquals(0f, w.victoryT, "the banner lap starts, the card lands after it");
    }

    @Test
    void lokaGateOpensOnlyOnVictory() {
        World w = arena();
        assertTrue(w.tileMapBoss.solidPx(2600, 20590), "the next loka is sealed behind gold");
        Enemy b = bossIn(w);
        assertTrue(b.hurt(99999, w), "the kill lands");
        assertTrue(w.bossDead);
        assertFalse(w.tileMapBoss.solidPx(2600, 20590), "the kill opens the gate");
    }

    @Test
    void shootsOnlyWithLineOfSight() {
        World w = arena();
        Enemy b = bossIn(w);
        w.enemies.clear();
        w.enemies.add(b);
        b.active = true;
        w.ship.x = 1200; // behind the annulus
        w.ship.y = 19900;
        assertFalse(w.clearLine(b.x, b.y, w.ship.x, w.ship.y), "test needs a blocked lane");
        for (int i = 0; i < 300; i++) b.update(1 / 60f, w, i / 60f); // 5s of telling off
        assertTrue(w.bullets.isEmpty(), "no release through rock");

        w.ship.x = 2600; // open lane, straight up the pit
        w.ship.y = 19600;
        assertTrue(w.clearLine(b.x, b.y, w.ship.x, w.ship.y), "test needs an open lane");
        for (int i = 0; i < 600 && w.bullets.isEmpty(); i++) b.update(1 / 60f, w, i / 60f);
        assertFalse(w.bullets.isEmpty(), "he releases into the open lane");
    }

    @Test
    void firstVolleyIsTheOrderForHisRing() {
        World w = arena();
        Enemy b = bossIn(w);
        w.enemies.clear();
        w.enemies.add(b);
        b.active = true;
        Enemy f = new Enemy(Enemy.Kind.VAKRA, 3000, 19104);
        f.commander = b;
        f.active = true;
        f.fireTimer = 99; // would never fire on its own within this test
        w.enemies.add(f);
        for (int i = 0; i < 180 && f.fireTimer > 1; i++) b.update(1 / 60f, w, i / 60f);
        assertTrue(f.fireTimer < 1, "the commander's volley releases his ring, got " + f.fireTimer);
    }

    @Test
    void stoodDownUnitsWalkOutAndRetire() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 2600;
        w.ship.y = 5000; // out of range: nothing else moves it
        Enemy e = new Enemy(Enemy.Kind.VAKRA, 2600, 19104);
        e.dismissed = true;
        e.active = true;
        w.enemies.add(e);
        for (int i = 0; i < 420; i++) e.update(1 / 60f, w, i / 60f); // 7s
        assertTrue(e.dead, "a stood-down unit retires after its exit walk");
        assertTrue(w.bullets.isEmpty(), "and never fires on the way out");
    }
}
