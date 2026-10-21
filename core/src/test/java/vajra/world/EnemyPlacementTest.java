package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.ShipDef;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EnemyPlacementTest {
    /** No input: the sim still runs (regionEntry, timers), the ship just does not fly. */
    private static final vajra.ship.ShipControls NONE = new vajra.ship.ShipControls() {
        public float thrustY() { return 0; }
        public float thrustX() { return 0; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return false; }
        public boolean dash() { return false; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    };

    @Test
    void theLokaCurveGradesTheRosterAndItsFire() {
        World w = new World();
        assertEquals(0, w.lokaIndex, "a run starts in Atala, the first loka");
        assertEquals(1f, LokaTable.at(0).hpScale, 0.001f, "Atala is the baseline");
        for (Enemy e : w.enemies) {
            assertEquals(e.maxHp, e.hp, 0.01f, e.kind + " is at full hull when placed");
            assertEquals(1f, e.dmgScale, 0.001f, "Atala fire is ungraded");
        }
        // the curve is monotonic: every deeper loka is at least as mean as the one above it
        for (int l = 1; l < LokaTable.COUNT; l++) {
            assertTrue(LokaTable.at(l).hpScale > LokaTable.at(l - 1).hpScale,
                    LokaTable.at(l).name + " out-scales " + LokaTable.at(l - 1).name);
            assertTrue(LokaTable.at(l).dmgScale >= LokaTable.at(l - 1).dmgScale,
                    LokaTable.at(l).name + " fire is not weaker than " + LokaTable.at(l - 1).name);
        }
        assertTrue(LokaTable.at(LokaTable.COUNT - 1).hpScale > 4f,
                "the deepest loka is a real step up, not another Atala");
    }

    @Test
    void theSevenLokasAreOrderedAndEachPaysSomething() {
        assertEquals(7, LokaTable.COUNT);
        assertEquals("Atala", LokaTable.at(0).name, "Atala is the first of the seven");
        assertEquals("Trivikrama", LokaTable.at(LokaTable.COUNT - 1).boss,
                "and Trivikrama holds the last band");
        for (int i = 0; i < LokaTable.COUNT; i++) {
            assertTrue(LokaTable.at(i).shards.length > 0, LokaTable.at(i).name + " pays shards");
            assertTrue(LokaTable.at(i).name != null && !LokaTable.at(i).name.isEmpty());
        }
        assertEquals(5, LokaTable.at(6).shards.length, "Patala pays all five elements");
        assertEquals(2, LokaTable.at(5).shards.length, "Rasatala is the first mixed harvest");
    }

    /** Each loka opens more hulls, and never fewer — the ship ladder is the progression spine. */
    @Test
    void hullsUnlockAsTheLokasDeepen() {
        assertTrue(LokaTable.shipUnlocked(0, 1), "Kite opens in Atala");
        assertFalse(LokaTable.shipUnlocked(0, 2), "Mk-3 waits for Vitala");
        assertTrue(LokaTable.shipUnlocked(1, 3));
        assertFalse(LokaTable.shipUnlocked(1, 6), "Vajra-7 waits for Talatala");
        assertTrue(LokaTable.shipUnlocked(3, 6));
        for (int i = 1; i < ShipDef.ALL.length; i++) {
            int l = ShipDef.ALL[i].minLoka;
            assertTrue(LokaTable.shipUnlocked(l, i), ShipDef.ALL[i].name + " opens at its own loka");
            assertFalse(l == 0 && LokaTable.shipUnlocked(0, i) && i > 1,
                    "Atala only opens the first pair");
        }
    }

    /** Bands are NOT lokas. Atala owns every region in the current build, so crossing into
     *  band 2 must not switch the shard economy over to Vitala's element mid-Atala. */
    @Test
    void crossingBandsNeverAdvancesTheLoka() {
        World w = new World();
        assertEquals(0, w.lokaIndex);
        for (int r = 0; r < w.regionStart.length; r++)
            assertEquals(0, w.lokaOfRegion[Math.min(r, w.lokaOfRegion.length - 1)],
                    "region " + r + " is still Atala");
        // fly the whole world: the deepest band is Atala's boss arena, still loka 0
        for (int r = 0; r < w.regionStart.length; r++) {
            w.ship.y = w.regionStart[r] + 10;
            w.update(1 / 60f, NONE);
            assertEquals(0, w.lokaIndex, "stayed in Atala at region " + r);
        }
        assertEquals(0, w.hangar.loka, "and the hangar agrees");
        assertEquals(0, LokaTable.at(w.lokaIndex).shards[0], "Atala still pays Akasha");
    }

    @Test
    void atalaOrderOfBattle() {
        World w = new World();
        // bands 1-5 + site guards; court() drops backup slots in tight chambers (openRoom < 0.5)
        assertEquals(227, w.enemies.size(), "full Atala placement (Bala joins when the seal lifts)");
    }

    @Test
    void nothingIsStationedInBhuLoka() {
        World w = new World();
        for (Enemy e : w.enemies)
            assertTrue(e.homeY >= w.regionStart[1], e.kind + " at " + e.homeY + " inside Bhu Loka");
    }

    @Test
    void placementIsMirrored() {
        World w = new World();
        for (Enemy e : w.enemies) {
            if (e.guard) continue; // shop/checkpoint guards are hand-placed, not mirrored
            if (Math.abs(e.homeX - 2600) < 1) continue;
            boolean found = false;
            for (Enemy o : w.enemies) {
                if (o.kind == e.kind && Math.abs(o.homeX - (5200 - e.homeX)) < 1
                        && Math.abs(o.homeY - e.homeY) < 1) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "no mirror for " + e.kind + " at " + e.homeX + "," + e.homeY);
        }
    }

    /** Guards the spawner, not the generator: World.add carves a padded box around every home
     *  before this runs, so it cannot fail on generator output — it fails if that margin shrinks. */
    @Test
    void homesAreNotLeftInsideRock() {
        World w = new World();
        for (Enemy e : w.enemies) {
            for (TileMap m : w.tileMaps()) {
                float[] xs = { e.homeX - e.w / 2, e.homeX + e.w / 2 };
                float[] ys = { e.homeY - e.h / 2, e.homeY + e.h / 2 };
                for (float cx : xs) {
                    for (float cy : ys) {
                        int tx = (int) Math.floor((cx - m.ox) / TileMap.TILE);
                        int ty = (int) Math.floor((cy - m.oy) / TileMap.TILE);
                        if (tx >= 0 && ty >= 0 && tx < m.cols && ty < m.rows) {
                            assertEquals(TileMap.EMPTY, m.get(tx, ty),
                                    e.kind + " corner inside rock at " + cx + "," + cy);
                        }
                    }
                }
            }
        }
    }

    @Test
    void sleepUntilApproachedThenPatrol() {
        World w = new World();
        Enemy auris = null;
        for (Enemy e : w.enemies) {
            if (e.kind == Enemy.Kind.AURIS) {
                auris = e;
                break;
            }
        }
        assertNotNull(auris);
        w.ship.x = auris.homeX + 5000;
        w.ship.y = auris.homeY;
        auris.update(0.016f, w, 0);
        assertFalse(auris.active);
        float x0 = auris.x;
        w.ship.x = auris.homeX;
        w.ship.y = auris.homeY;
        auris.update(0.016f, w, 1);
        assertTrue(auris.active);
        auris.update(0.5f, w, 2);
        assertNotEquals(x0, auris.x, "auris should patrol once awake");
    }

    /** CALL TO ARMS: a hit in the top three regions wakes sleepers within ARMS. */
    @Test
    void hitWakesNeighboursInTheTopRegions() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 2600;
        w.ship.y = 20400; // parked far away: proximity must not be what wakes anyone
        Enemy a = new Enemy(Enemy.Kind.MAYAVI, 2600, 13000);
        Enemy b = new Enemy(Enemy.Kind.HATAKA, 2850, 13000); // 250px out, asleep
        w.enemies.add(a);
        w.enemies.add(b);
        assertFalse(b.active);
        a.hurt(5, w);
        assertTrue(a.active, "the hit unit wakes itself");
        assertTrue(b.active, "the fight wakes the neighbours");
    }

    /** ...and the bottom three regions never hear it: only player proximity wakes them. */
    @Test
    void bottomRegionsStayAsleepOnHits() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 2600;
        w.ship.y = 20400;
        Enemy a = new Enemy(Enemy.Kind.MAYAVI, 2600, 3000);
        Enemy b = new Enemy(Enemy.Kind.HATAKA, 2850, 3000);
        w.enemies.add(a);
        w.enemies.add(b);
        a.hurt(5, w);
        assertTrue(a.active);
        assertFalse(b.active, "the bottom half stays asleep");
    }
}
