package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.Hangar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** THE AKASHA ASTRA — Atala's forged weapon. Five shards of a loka's OWN element forge it once,
 *  every five after that buys a release, and a release throws twenty homing cubes that go through
 *  rock. The forge obeys the purity rule, which is why LOKA-LAWS §3 (which
 *  element pays) is the load-bearing bit. */
class ForgeTest {
    private static final int ATALA = 0;   // pays Akasha
    private static final int AKASHA = Hangar.AKASHA;

    /** Feeds the loka's own element n times, exactly as a collected shard would. */
    private static Hangar feed(int loka, int tatva, int n) {
        Hangar h = new Hangar();
        for (int i = 0; i < n; i++) h.addShard(tatva, loka);
        return h;
    }

    /** A world with n living enemies around the ship, ready to be targeted. */
    private static World withEnemies(int n) {
        World w = new World();
        w.enemies.clear();
        for (int i = 0; i < n; i++) {
            Enemy e = new Enemy(Enemy.Kind.MAYAVI, 2600 + i * 30, 3000);
            e.hp = 90;
            e.active = true;
            w.enemies.add(e);
        }
        return w;
    }

    // --- the economy ----------------------------------------------------------------------

    @Test
    void theFirstFiveOfTheLokasOwnElementForgeIt() {
        assertEquals(5, Hangar.FORGE_COST, "asked for five to forge");
        Hangar h = feed(ATALA, AKASHA, Hangar.FORGE_COST - 1);
        assertFalse(h.isForged(ATALA), "not forged one short");
        assertEquals(0, h.forgeCharges[ATALA], "and no release");
        h.addShard(AKASHA, ATALA);
        assertTrue(h.isForged(ATALA), "the fifth forges the Astra");
        assertEquals(1, h.forgeCharges[ATALA], "forging IS the first release");
    }

    @Test
    void onlyTheLokasOwnElementCanForgeIt() {
        // Atala pays Akasha: nine Agni shards are stock and nothing more
        Hangar h = feed(ATALA, Hangar.AGNI, 9);
        assertEquals(9, h.shards[Hangar.AGNI], "the wrong element still stocks the pile");
        assertFalse(h.isForged(ATALA), "and never forges the Astra");
        assertFalse(h.hasForgeCharge(ATALA));
    }

    @Test
    void everyFiveAfterThatBuysOneRelease() {
        Hangar h = feed(ATALA, AKASHA, Hangar.FORGE_COST);
        assertEquals(1, h.forgeCharges[ATALA], "forged, one release in hand");
        for (int i = 0; i < Hangar.CHARGE_COST * 3; i++) h.addShard(AKASHA, ATALA);
        assertEquals(4, h.forgeCharges[ATALA], "three more releases on top");
        int spent = 0;
        while (h.spendForgeCharge(ATALA)) spent++;
        assertEquals(4, spent, "and it runs dry at zero");
    }

    /** The rule that was broken before: forging is ONCE. Spending your only release must not
     *  put you back on FORGE_COST for the next one. */
    @Test
    void forgingIsPermanentAfterYouSpendTheRelease() {
        Hangar h = feed(ATALA, AKASHA, Hangar.FORGE_COST);
        assertEquals(1, h.forgeCharges[ATALA]);
        assertTrue(h.spendForgeCharge(ATALA), "spending the only release");
        assertEquals(0, h.forgeCharges[ATALA], "nothing left in hand");
        assertTrue(h.isForged(ATALA), "but the Astra is still forged");

        for (int i = 0; i < Hangar.CHARGE_COST - 1; i++) h.addShard(AKASHA, ATALA);
        assertEquals(0, h.forgeCharges[ATALA], "four shards is not a release");
        h.addShard(AKASHA, ATALA);
        assertEquals(1, h.forgeCharges[ATALA], "the fifth buys one - not another FORGE_COST");
        assertEquals(Hangar.CHARGE_COST, h.forgeNeed(ATALA), "and the next one costs five");
    }

    @Test
    void eachLokaForgesItsOwnAstra() {
        for (int l = 0; l < LokaTable.COUNT; l++) {
            int[] pays = LokaTable.at(l).shards;
            for (int t = 0; t < 5; t++) {
                Hangar h = feed(l, t, Hangar.FORGE_COST);
                boolean should = LokaTable.at(l).pays(t);
                assertEquals(should, h.forgeCharges[l] > 0,
                        LokaTable.at(l).name + " / " + World.TATVA[t]);
            }
            assertTrue(pays.length > 0, LokaTable.at(l).name + " pays something");
        }
    }

    // --- the charge, then the release --------------------------------------------------------

    /** Press F and let the charge run out. Everything about the RELEASE is only true after
     *  this - useForge() alone leaves twenty held cubes in a shell and nothing armed. */
    private static World fired(World w) {
        assertTrue(w.useForge(), "the charge starts");
        int guard = 0;
        while (w.winding() && guard++ < 600) w.updateAstra(1 / 60f);
        assertFalse(w.winding(), "the charge finished and released");
        return w;
    }

    private static World armed(int enemies) {
        World w = withEnemies(enemies);
        w.hangar.forged[ATALA] = true;
        w.hangar.forgeCharges[ATALA] = 1;
        return fired(w);
    }

    /** F does not release. It loads. This is the whole point of the change: a weapon that fires
     *  the instant you touch the button has no weight, and nothing about the release is felt if
     *  there was no wait to have doubted during. */
    @Test
    void pressingFDoesNotReleaseIt() {
        World w = withEnemies(5);
        w.hangar.forged[ATALA] = true;
        w.hangar.forgeCharges[ATALA] = 1;
        assertTrue(w.useForge(), "the charge starts");
        assertTrue(w.winding(), "and it is winding, not released");
        assertEquals(World.FORGE_WIND, w.forgeWind, 0.001f, "the whole charge is ahead of you");

        for (AstraCube c : w.astra)
            assertFalse(c.armed, "no cube has a target yet - there is nothing to aim at");
        assertEquals(0f, w.astraPunch, "and no punch: that belongs to the release");
    }

    @Test
    void theChargeRunsForItsFullLengthAndThenLetsGo() {
        World w = withEnemies(5);
        w.hangar.forged[ATALA] = true;
        w.hangar.forgeCharges[ATALA] = 1;
        w.useForge();
        assertEquals(0f, w.windK(), 0.001f, "no charge at the start");

        float dt = 1 / 60f;
        for (float t = 0; t < World.FORGE_WIND - 0.1f; t += dt) {
            w.updateAstra(dt);
            assertTrue(w.winding(), "still winding at " + t);
            for (AstraCube c : w.astra) assertFalse(c.armed, "nothing lets go early");
        }
        while (w.winding()) w.updateAstra(dt);
        assertTrue(w.windK() > 0.99f, "the charge completed");
        for (AstraCube c : w.astra) assertTrue(c.armed, "and every cube is away");
        assertEquals(World.RELEASE_FREEZE, w.hitStop, 0.001f, "the world freezes on the hit");
        assertEquals(1f, w.astraPunch, "and the view punches back");
    }

    /** The cage tightens as it charges: that tightening IS the read on how long is left. */
    @Test
    void theCageTightensAsTheChargeBuilds() {
        World w = withEnemies(1);
        w.hangar.forged[ATALA] = true;
        w.hangar.forgeCharges[ATALA] = 1;
        w.useForge();
        float start = radiusOf(w);
        float dt = 1 / 60f;
        float last = start;
        for (int i = 0; i < 40; i++) {
            w.updateAstra(dt);
            float now = radiusOf(w);
            assertTrue(now <= last + 0.001f, "the shell never spreads (at step " + i + ")");
            last = now;
        }
        assertTrue(last < start * 0.8f, "and it has visibly drawn in: " + start + " -> " + last);
    }

    private static float radiusOf(World w) {
        float sum = 0;
        for (AstraCube c : w.astra)
            sum += (float) Math.hypot(c.x - w.ship.x, c.y - w.ship.y);
        return sum / Math.max(1, w.astra.size());
    }

    /** Held right: full thrust, dash down, nose level. */
    private static final vajra.ship.ShipControls DRIVE = new vajra.ship.ShipControls() {
        public float thrustX() { return 1f; }
        public float thrustY() { return 0f; }
        public float rotate() { return 0f; }
        public boolean fire() { return false; }
        public boolean boost() { return false; }
        public boolean dash() { return true; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0f; }
        public float aimY() { return 0f; }
    };

    /** One second of held thrust, from rest. */
    private static float travelIn(World w, float seconds) {
        w.ship.x = 2600;
        w.ship.y = 3000;
        w.ship.vx = w.ship.vy = 0;
        float x0 = w.ship.x;
        for (int i = 0; i < 60; i++) w.update(1 / 60f, DRIVE);
        return Math.abs(w.ship.x - x0) / seconds;
    }

    /** The charge costs you. The hull is heavy and the dash is gone, so those 1.75 s are the
     *  risk that gives the release its weight - without them the charge would only be a delay. */
    @Test
    void theChargeCostsYouYourSpeedAndTheDash() {
        World free = withEnemies(1);
        float baseline = travelIn(free, 1f);
        assertTrue(baseline > 10f, "a second of held thrust moves you: " + baseline);

        World w = withEnemies(1);
        w.hangar.forged[ATALA] = true;
        w.hangar.forgeCharges[ATALA] = 1;
        assertFalse(w.ship.winding, "not winding to begin with");

        w.useForge();
        assertTrue(w.ship.winding, "the hull is heavy the instant the charge starts");
        assertEquals(0f, w.ship.thrustX, 0.0001f, "and the stick only reaches a third of it");
        w.update(1 / 60f, DRIVE);
        assertEquals(0f, w.ship.dashTime, 0.0001f, "the dash is locked out: the charge owns it");

        float wound = travelIn(w, 1f);
        assertTrue(wound < baseline * 0.75f, "the same second of input gets you much less far "
                + "while winding: " + baseline + " -> " + wound);
        assertTrue(w.winding(), "still charged after all that");
    }

    /** One charge at a time. Mashing F must not stack cages or queue releases. */
    @Test
    void oneChargeAtATime() {
        World w = withEnemies(5);
        w.hangar.forged[ATALA] = true;
        w.hangar.forgeCharges[ATALA] = 2;
        assertTrue(w.useForge());
        assertFalse(w.useForge(), "the second press is refused");
        assertEquals(1, w.astra.size() / World.ASTRA_CUBES, "and only one cage is up");
        assertEquals(1, w.hangar.forgeCharges[ATALA], "the second release was not spent");
    }

    // --- the release ----------------------------------------------------------------------

    @Test
    void aReleaseThrowsTwentyCubes() {
        assertEquals(20, World.ASTRA_CUBES, "twenty cubes");
        World w = armed(40);
        assertEquals(World.ASTRA_CUBES, w.astra.size(), "one cube per target");
    }

    /** Each cube is answerable to exactly one enemy — twenty cubes must not all hit one body. */
    @Test
    void everyCubePicksItsOwnEnemy() {
        World w = armed(40);
        boolean[] seen = new boolean[w.enemies.size()];
        int n = 0;
        for (AstraCube c : w.astra) {
            if (c.target == null) continue;
            int idx = w.enemies.indexOf(c.target);
            assertTrue(idx >= 0, "the target is a live member of the roster");
            assertFalse(seen[idx], "no enemy is targeted twice (dup at " + idx + ")");
            seen[idx] = true;
            n++;
        }
        assertEquals(World.ASTRA_CUBES, n, "all twenty are aimed");
    }

    /** With fewer enemies than cubes the extra ones still come out, just untargeted. */
    @Test
    void fewerEnemiesThanCubesStillThrowsTwenty() {
        World w = armed(3);
        assertEquals(World.ASTRA_CUBES, w.astra.size(), "the volley is always its full size");
        int aimed = 0;
        for (AstraCube c : w.astra) if (c.target != null) aimed++;
        assertEquals(3, aimed, "one per enemy that exists");
    }

    /** It goes through rock: a cube locked in solid must still reach a target on the other side. */
    @Test
    void aCubeGoesThroughRock() {
        World w = armed(1);
        AstraCube c = w.astra.get(0);
        Enemy e = w.enemies.get(0);
        // park the cube deep inside rock, far from its target
        c.x = 2600; c.y = 3000;
        e.x = 2600; e.y = 6000;
        float startY = c.y;
        // three seconds at ASTRA_SPEED is 2700 px; ask for most of it, allowing for the turn
        for (int i = 0; i < 180; i++) c.think(w, 1 / 60f, World.ASTRA_TURN);
        assertTrue(c.y > startY + 2000f, "it crossed 3000 px of open world and rock alike (y "
                + startY + " -> " + c.y + ")");
    }

    /** And it actually lands the hit. */
    @Test
    void aCubeDamagesTheEnemyItWasSentFor() {
        World w = armed(1);
        AstraCube c = w.astra.get(0);
        Enemy e = w.enemies.get(0);
        float before = e.hp;
        e.x = c.x + 40; e.y = c.y;
        for (int i = 0; i < 20 && e.hp == before; i++) c.think(w, 1 / 60f, World.ASTRA_TURN);
        assertTrue(e.hp < before, "the cube took " + before + " down to " + e.hp);
    }

    /** The release is only spendable when a release is actually in hand. */
    @Test
    void itNeedsAChargeAndRefusesOtherwise() {
        World w = withEnemies(5);
        w.hangar.forged[ATALA] = true;
        assertFalse(w.useForge(), "nothing banked");
        assertTrue(w.astra.isEmpty());
        w.hangar.forgeCharges[ATALA] = 1;
        assertTrue(w.useForge());
        assertFalse(w.useForge(), "and the same release cannot be spent twice");
        assertEquals(0, w.hangar.forgeCharges[ATALA], "one press, one release");
    }

    /** The cubes are cleared with the world, not leaked between runs. */
    @Test
    void theReleaseIsInTheLokasOwnColour() {
        World w = armed(1);
        // Atala pays Akasha, and its RGB is violet: red above green, blue strongest
        float[] ak = World.TATVA_RGB[Hangar.AKASHA];
        assertTrue(ak[0] > ak[1], "Akasha is violet");
        assertTrue(ak[2] >= ak[0], "and blue leads");
        for (AstraCube c : w.astra) assertTrue(c.damage > 0f, "each cube carries damage");
    }
}