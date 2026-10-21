package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.ShipControls;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Score & combo: kill values ride the dropLoot tiers plus kind hp, the chain multiplies and
 *  dies (window, death), formation / region / checkpoint / boss bonuses pay flat, and the
 *  total saturates at the ten digits instead of overflowing. */
class ScoreTest {
    private static final ShipControls NONE = new ShipControls() {
        public float thrustX() { return 0; }
        public float thrustY() { return 0; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return false; }
        public boolean dash() { return false; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    };

    private static Enemy foe(Enemy.Kind kind) {
        return new Enemy(kind, 1000, 800);
    }

    @Test
    void killPaysTierPlusKindHp() {
        World w = new World();

        Score s = new Score();
        s.kill(w, foe(Enemy.Kind.LANCE));            // rank0: 50 tier + 50 hp
        assertEquals(100, s.points);
        assertEquals(1, s.combo);
        assertEquals(Score.WINDOW, s.comboT, 0.0001f, "the chain opens its window");

        s = new Score();
        Enemy elite = foe(Enemy.Kind.LANCE);
        elite.elite = true;
        s.kill(w, elite);                            // elite tier: 600 + 50
        assertEquals(650, s.points);

        s = new Score();
        Enemy lead = foe(Enemy.Kind.LANCE);
        lead.rank = 1;
        s.kill(w, lead);                             // leader: 250 + 50 + flat 250 break
        assertEquals(550, s.points);

        s = new Score();
        Enemy bal = foe(Enemy.Kind.BALA);
        bal.boss = true;
        bal.rank = 1;
        s.kill(w, bal);                              // boss: 3000 flat tier + its own 1500 hp
        assertEquals(4500, s.points);

        s = new Score();
        s.kill(w, foe(Enemy.Kind.HATAKA));           // every kind rides its own hp
        assertEquals(95, s.points);
    }

    @Test
    void comboMultiplierStepsAtFiveKillMarks() {
        Score s = new Score();
        assertEquals(1, s.mult());
        s.combo = 4;  assertEquals(1, s.mult());
        s.combo = 5;  assertEquals(2, s.mult());
        s.combo = 9;  assertEquals(2, s.mult());
        s.combo = 10; assertEquals(3, s.mult());
        s.combo = 14; assertEquals(3, s.mult());
        s.combo = 15; assertEquals(4, s.mult());
        s.combo = 19; assertEquals(4, s.mult());
        s.combo = 20; assertEquals(5, s.mult());
        s.combo = 99; assertEquals(5, s.mult(), "x5 is the ceiling");
    }

    @Test
    void theNewTierPaysImmediately() {
        World w = new World();
        Score s = new Score();
        s.combo = 4; // one kill off the x2 mark
        s.kill(w, foe(Enemy.Kind.LANCE));
        assertEquals(5, s.combo);      // bumped first...
        assertEquals(2, s.mult());
        assertEquals(200, s.points);   // ...so this very kill already pays double
    }

    @Test
    void windowExpiryDropsTheChain() {
        Score s = new Score();
        s.combo = 3;
        s.comboT = Score.WINDOW;
        s.update(2.9f);
        assertEquals(3, s.combo, "still inside the window");
        s.update(0.2f);
        assertEquals(0, s.combo, "window ran out: chain dropped");
        assertEquals(0f, s.comboT);
        assertEquals(1, s.mult());
    }

    @Test
    void deathBreaksTheChain() {
        World w = new World();
        w.score.combo = 7;
        w.score.comboT = Score.WINDOW;
        w.ship.hp = 1;
        w.ship.invuln = 0;
        w.ship.damage(999, w);
        assertEquals(0, w.score.combo, "the hull popped: x1 again");
        assertEquals(0f, w.score.comboT);
    }

    @Test
    void saturatesAtMax() {
        World w = new World();
        Score s = new Score();
        s.add(Long.MAX_VALUE);
        assertEquals(Score.MAX, s.points, "clamped at the ten digits");
        s.add(1);
        assertEquals(Score.MAX, s.points, "and stays there");
        s.points = Score.MAX - 10;
        s.kill(w, foe(Enemy.Kind.LANCE)); // a 100-point kill would overshoot
        assertEquals(Score.MAX, s.points, "the kill clamps instead of overflowing");
    }

    @Test
    void formationFallPaysBreakThenWipe() {
        World w = new World();
        w.enemies.clear();
        Enemy lead = foe(Enemy.Kind.LANCE);
        lead.rank = 1;
        Enemy m1 = foe(Enemy.Kind.LANCE);
        m1.commander = lead;
        Enemy m2 = foe(Enemy.Kind.LANCE);
        m2.commander = lead;
        w.enemies.add(lead);
        w.enemies.add(m1);
        w.enemies.add(m2);

        assertTrue(m1.hurt(9999, w));
        assertEquals(100, w.score.points, "a member over a living leader: kill value only");
        assertTrue(lead.hurt(9999, w));
        assertEquals(650, w.score.points, "the leader falls: +550 tier, hp and the break");
        assertTrue(m2.hurt(9999, w));
        assertEquals(1250, w.score.points, "last one down: +600, kill value and the wipe");
        assertEquals(3, w.score.combo);
    }

    @Test
    void regionEntryPaysOncePerBand() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 40; // edge lane: nothing in the way
        w.ship.y = 500;
        w.update(1 / 60f, NONE);
        assertEquals(0, w.score.points, "the spawn band is pre-scored");
        w.ship.y = w.regionStart[1] + 100;
        w.update(1 / 60f, NONE);
        assertEquals(Score.REGION, w.score.points, "first crossing pays");
        w.ship.y = 500;
        w.update(1 / 60f, NONE);
        assertEquals(Score.REGION, w.score.points, "flying back re-pays nothing");
        w.ship.y = w.regionStart[1] + 100;
        w.update(1 / 60f, NONE);
        assertEquals(Score.REGION, w.score.points, "and neither does re-entering");
    }

    @Test
    void checkpointPaysOnce() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 40;
        w.ship.y = w.regionStart[1] + 100;
        w.update(1 / 60f, NONE);
        assertEquals(Score.REGION, w.score.points, "the crossing into band 1 already paid");
        Checkpoint c = w.checkpoints[0];
        w.ship.x = c.x;
        w.ship.y = c.y;
        w.update(1 / 60f, NONE);
        assertTrue(c.collected, "touching the collector spends it");
        assertEquals(Score.REGION + Score.CHECKPOINT, w.score.points);
        w.update(1 / 60f, NONE);
        assertEquals(Score.REGION + Score.CHECKPOINT, w.score.points, "spent: never pays twice");
    }

    @Test
    void bossFallPaysFlat() {
        World w = new World();
        w.enemies.clear();
        Enemy bal = new Enemy(Enemy.Kind.BALA, 2600, 19104);
        bal.boss = true;
        bal.rank = 1;
        w.victory(bal);
        assertEquals(Score.BOSS, w.score.points, "Bala's fall pays the flat bonus");
        assertEquals(0, w.score.combo, "milestones never extend the chain");
    }
}
