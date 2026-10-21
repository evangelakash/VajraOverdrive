package vajra.world;

import vajra.enemy.Enemy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** The band caches: one per Atala band just inside its entrance gate (never drawn). Touching one
 *  wakes the local squad inside the radius â€” site guards excluded â€” and clearing it pays 100 coins
 *  + a life. Also holds the tier-speed rule (kind cruise, rank on top). */
class CacheTest {
    private static boolean solid(World w, float x, float y) {
        for (TileMap m : w.tileMaps()) if (m.solidPx(x, y)) return true;
        return false;
    }

    private static float dist(Enemy e, float x, float y) {
        return (float) Math.hypot(e.x - x, e.y - y);
    }

    @Test
    void cacheStateIsPerSeatNotPerBand() {
        World w = new World();
        // seat 0 opens the first band's stash; every other seat's copy of that band is untouched
        w.slot = 0;
        w.resolveCaches();
        w.touchCache(0);
        w.resolveCaches();
        w.cacheState[0][0] = World.CACHE_DONE; // as the solo flow would leave it
        for (int seat = 1; seat < World.SLOTS; seat++)
            assertEquals(World.CACHE_SEALED, w.cacheState[seat][0],
                    "seat " + seat + " did not open seat 0's stash");
    }

    @Test
    void oneCachePerBandJustInsideTheEntrance() {
        World w = new World();
        assertEquals(5, w.caches.length, "one per Atala band, none in Bhu");
        for (int i = 0; i < 5; i++) {
            int r = i + 1;
            float cx = w.caches[i][0], cy = w.caches[i][1];
            assertTrue(cy >= w.regionStart[r] && cy <= w.regionEnd[r], "inside its band");
            assertTrue(cy < w.regionStart[r] + 600, "just after the entrance gate, not before it");
            assertFalse(solid(w, cx, cy), "on open ground");
            assertEquals(World.CACHE_SEALED, w.cacheState[w.slot][i], "starts sealed");
        }
    }

    @Test
    void onlyTheLocalSquadAnswers() {
        World w = new World();
        w.enemies.clear();
        float cx = w.caches[0][0], cy = w.caches[0][1];
        Enemy near = new Enemy(Enemy.Kind.VAKRA, cx + 300, cy);  // inside the radius
        Enemy far = new Enemy(Enemy.Kind.VAKRA, cx + 1400, cy);  // well outside it
        w.enemies.add(near);
        w.enemies.add(far);
        w.touchCache(0);
        assertEquals(World.CACHE_CONTESTED, w.cacheState[w.slot][0], "the local squad answers");
        assertTrue(near.hunt, "inside the radius: comes for you");
        assertFalse(far.hunt, "outside the radius: left alone");
    }

    @Test
    void siteGuardsNeverAnswerTheCache() {
        World w = new World();
        w.enemies.clear();
        float cx = w.caches[0][0], cy = w.caches[0][1];
        Enemy site = new Enemy(Enemy.Kind.LANCE, cx + 100, cy + 60);
        site.siteGuard = true; // as placed on every shop and checkpoint post
        w.enemies.add(site);
        int coins = w.hangar.coins, lives = w.lives;
        w.touchCache(0);
        assertEquals(World.CACHE_DONE, w.cacheState[w.slot][0], "the guard holds its site: nobody answers");
        assertFalse(site.hunt, "and it never starts hunting");
        assertEquals(coins + 100, w.hangar.coins, "nobody answers: the stash pays on the touch");
        assertEquals(lives + 1, w.lives, "+1 life");
    }

    @Test
    void paysOutWhenTheSquadFalls() {
        World w = new World();
        w.enemies.clear(); // only our squad answers: the result is deterministic
        float cx = w.caches[0][0], cy = w.caches[0][1];
        Enemy e = new Enemy(Enemy.Kind.VAKRA, cx + 120, cy + 40);
        w.enemies.add(e);
        w.touchCache(0);
        assertEquals(World.CACHE_CONTESTED, w.cacheState[w.slot][0], "the squad answers");
        assertTrue(e.hunt && e.active, "convergers are marked and woken");
        int coins = w.hangar.coins, lives = w.lives;
        w.touchCache(0); // contested: no payout yet
        assertEquals(coins, w.hangar.coins, "no pay while they live");
        w.resolveCaches();
        assertEquals(World.CACHE_CONTESTED, w.cacheState[w.slot][0], "one still alive");
        assertTrue(e.hurt(9999, w));
        w.resolveCaches();
        assertEquals(World.CACHE_DONE, w.cacheState[w.slot][0], "the last fall pays out on the spot");
        assertEquals(coins + 100, w.hangar.coins, "100 coins");
        assertEquals(lives + 1, w.lives, "+1 life");
        assertFalse(w.effects.isEmpty(), "the payment ring still marks the stash");
        w.touchCache(0); // paid: touching again pays nothing
        assertEquals(coins + 100, w.hangar.coins, "no double pay");
        assertEquals(lives + 1, w.lives, "no double life");
    }

    @Test
    void emptyPocketPaysOnFirstTouch() {
        World w = new World();
        w.enemies.clear();
        int coins = w.hangar.coins, lives = w.lives;
        w.touchCache(0);
        assertEquals(World.CACHE_DONE, w.cacheState[w.slot][0], "nobody home: paid on the spot");
        assertEquals(coins + 100, w.hangar.coins, "no fumble on an invisible point");
        assertFalse(w.effects.isEmpty(), "the payout still rings for you");
        w.touchCache(0);
        assertEquals(coins + 100, w.hangar.coins, "no double pay");
    }

    @Test
    void huntersConvergeAndOutrunTheirLeash() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 2600;
        w.ship.y = 3000;
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 3400, 3600);
        e.active = true;
        e.hunt = true; // the cache called: straight at the ship, leash be damned
        w.enemies.add(e);
        float d0 = dist(e, w.ship.x, w.ship.y);
        for (int i = 0; i < 300; i++) e.update(1 / 60f, w, i / 60f); // 5s
        float d1 = dist(e, w.ship.x, w.ship.y);
        assertTrue(d1 < d0, "hunters close in: " + d0 + " -> " + d1);
        assertTrue(dist(e, e.homeX, e.homeY) > 300, "and leave the leash behind");
    }

    @Test
    void everyTierHasItsOwnSpeedAndRankRunsHotter() {
        assertTrue(Enemy.Kind.HATAKA.speed < Enemy.Kind.LANCE.speed, "turret plods");
        assertTrue(Enemy.Kind.LANCE.speed < Enemy.Kind.AURIS.speed, "kiter beats the turret");
        assertTrue(Enemy.Kind.AURIS.speed < Enemy.Kind.MAYAVI.speed, "mid tier faster");
        assertTrue(Enemy.Kind.MAYAVI.speed < Enemy.Kind.VAKRA.speed, "weaver faster still");
        assertTrue(Enemy.Kind.VAKRA.speed < Enemy.Kind.BHAVI.speed, "berserker fastest");

        World w = new World();
        w.enemies.clear();
        w.ship.x = 2600;
        w.ship.y = 800;
        Enemy foot = new Enemy(Enemy.Kind.MAYAVI, 2500, 1500);
        Enemy lead = new Enemy(Enemy.Kind.MAYAVI, 2700, 1500);
        lead.rank = 1;
        foot.active = lead.active = true;
        foot.hunt = lead.hunt = true;
        w.enemies.add(foot);
        w.enemies.add(lead);
        float f0 = dist(foot, foot.spawnX, foot.spawnY);
        float l0 = dist(lead, lead.spawnX, lead.spawnY);
        for (int i = 0; i < 180; i++) {
            foot.update(1 / 60f, w, i / 60f);
            lead.update(1 / 60f, w, i / 60f);
        }
        float fGo = dist(foot, foot.spawnX, foot.spawnY) - f0;
        float lGo = dist(lead, lead.spawnX, lead.spawnY) - l0;
        assertTrue(lGo > fGo * 1.05f, "the rank-1 lead covers more ground: " + lGo + " vs " + fGo);
    }

    @Test
    void everyTorchIsMountedOnItsWall() {
        World w = new World();
        assertTrue(w.torches.length >= 12, "the gates and checkpoints keep their pairs: "
                + w.torches.length);
        for (float[] t : w.torches) {
            assertTrue(w.blockedPx(t[3], t[4]), "stored wall sample is rock: " + java.util.Arrays.toString(t));
            assertTrue(Math.hypot(t[3] - t[0], t[4] - t[1]) <= 6, "rock right behind the base: " + java.util.Arrays.toString(t));
        }
        boolean grandGate = false; // the 7-tile doorway's jambs take a sideways pair
        for (float[] t : w.torches)
            if (Math.abs(t[1] - 1600) < 1 && Math.abs(t[2]) == 90) grandGate = true;
        assertTrue(grandGate, "the grand gate doorway is framed in fire");
    }

    @Test
    void plantsGripAnyRockFaceAndMayaIsThick() {
        World w = new World();
        assertTrue(w.plants.length >= 1000, "the scatter found faces: " + w.plants.length);
        int maya = 0;
        for (float[] p : w.plants) {
            // {x, y, variant, phase, angle, size, sampleX, sampleY}: root 4 px behind its rock
            assertTrue(w.blockedPx(p[6], p[7]), "sample is rock: " + java.util.Arrays.toString(p));
            double reach = Math.hypot(p[0] - p[6], p[1] - p[7]);
            assertTrue(reach > 3.5 && reach < 4.5, "root sits 4 px behind the face: " + reach);
            assertTrue(p[2] >= 0 && p[2] <= 3, "one of the four variants");
            assertTrue(p[4] == 0 || p[4] == 90 || p[4] == -90 || p[4] == 180, "grows off a face");
            assertTrue(p[5] == 1, "all plants stand 1x");
            float tx = p[4] == 0 || p[4] == 180 ? TileMap.TILE : 0, ty = tx == 0 ? TileMap.TILE : 0;
            float nx = p[4] == -90 ? -1 : p[4] == 90 ? 1 : 0, ny = p[4] == 0 ? -1 : p[4] == 180 ? 1 : 0;
            boolean runL = w.blockedPx(p[6] + tx, p[7] + ty) || w.blockedPx(p[6] + tx + nx * 8, p[7] + ty + ny * 8);
            boolean runR = w.blockedPx(p[6] - tx, p[7] - ty) || w.blockedPx(p[6] - tx + nx * 8, p[7] - ty + ny * 8);
            assertTrue(runL && runR, "never on a corner block: " + java.util.Arrays.toString(p));
            float gx = p[4] == -90 ? 1 : p[4] == 90 ? -1 : 0;
            float gy = p[4] == 0 ? 1 : p[4] == 180 ? -1 : 0;
            for (float s = 8; s <= 32 * p[5]; s += 8)
                assertFalse(w.blockedPx(p[0] + gx * s, p[1] + gy * s), "grows clear of rock");
            if (p[1] >= 4100 && p[1] < 7600) maya++;
            for (Checkpoint c : w.checkpoints)
                assertTrue(Math.hypot(p[0] - c.x, p[1] - c.y) > 60, "clear of checkpoints");
        }
        assertTrue(maya * 2 > w.plants.length,
                "Maya Vana holds the majority: " + maya + " of " + w.plants.length);
    }

    @Test
    void grassTuftsTouchAtOneUniformGap() {
        World w = new World();
        int touching = 0;
        for (int i = 0; i < w.plants.length; i++)
            for (int j = i + 1; j < w.plants.length; j++) {
                float[] a = w.plants[i], b = w.plants[j];
                double d = Math.hypot(a[0] - b[0], a[1] - b[1]);
                assertTrue(d >= WorldGen.PLANT_GAP - 0.1,
                        "nothing stands closer than the touch gap: " + d);
                // run mates are axis-exact: off-axis coordinate identical, on-axis PLANT_GAP apart
                if (Math.abs(d - WorldGen.PLANT_GAP) < 0.01 && (a[0] == b[0] || a[1] == b[1])) {
                    assertTrue(a[2] == 3 && b[2] == 3, "run mates are grass: "
                            + java.util.Arrays.toString(a) + " " + java.util.Arrays.toString(b));
                    touching++;
                }
            }
        assertTrue(touching > 0, "grass tufts stand side by side touching");
    }
}



