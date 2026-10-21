package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.ShipControls;
import vajra.weapon.Bullet;
import vajra.weapon.WeaponDef;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** The part that used to be decorative: shots must actually kill things, and get killed back. */
class CombatTest {
    private static ShipControls controls(final boolean shield) {
        return new ShipControls() {
            public float thrustX() { return 0; }
            public float thrustY() { return 0; }
            public float rotate() { return 0; }
            public boolean fire() { return false; }
            public boolean boost() { return false; }
            public boolean dash() { return false; }
            public boolean shield() { return shield; }
            public boolean mouseAim() { return false; }
            public float aimX() { return 0; }
            public float aimY() { return 0; }
        };
    }

    private static final ShipControls IDLE = controls(false);

    /** Empty arena, ship parked with a fixed nose so shots are predictable. y=800 is bare Bhu Loka. */
    private World arena(float x, float y) {
        World w = new World();
        w.enemies.clear();
        w.ship.x = x;
        w.ship.y = y;
        w.ship.vx = 0;
        w.ship.vy = 0;
        w.ship.facing = 0;
        return w;
    }

    private Enemy put(World w, Enemy.Kind kind, float x, float y) {
        Enemy e = new Enemy(kind, x, y);
        e.active = true;
        e.sentry = true; // pinned on its post: no kind flair, no patrol drift — only the gun moves
        w.enemies.add(e);
        return e;
    }

    /** One trigger pull from the muzzle, then let it fly. */
    private void shoot(World w, WeaponDef def) {
        w.spawnBullet(w.ship.x + 22, w.ship.y, 0, def, 1f, 1f);
    }

    @Test
    void bulletsChipAwayAndKill() {
        World w = arena(2600, 800);
        Enemy e = put(w, Enemy.Kind.VAKRA, 2800, 800);
        float hp0 = e.hp;
        shoot(w, WeaponDef.SINGLE);
        for (int i = 0; i < 120 && !w.bullets.isEmpty(); i++) w.update(0.016f, IDLE);
        assertTrue(e.hp < hp0, "a hit lands");
        assertTrue(e.active, "getting shot wakes you up");
        while (!e.dead) {
            shoot(w, WeaponDef.SINGLE);
            // stop the frame it dies: enemy fire lingers in flight, and a post-kill loop can
            // outlive the 1.3 s popup the kill pushes
            for (int i = 0; i < 120 && !e.dead && !w.bullets.isEmpty(); i++) w.update(0.016f, IDLE);
        }
        assertEquals(0f, e.hp, 0.001f);
        w.update(0.016f, IDLE);
        assertFalse(w.enemies.contains(e), "the dead are cleared out of the world");
        assertTrue(w.popups.anyActive(), "the kill pushes a score popup");
    }

    @Test
    void piercingShotRunsTheWholeLine() {
        World w = arena(2600, 800);
        Enemy near = put(w, Enemy.Kind.VAKRA, 2700, 800);
        Enemy far = put(w, Enemy.Kind.VAKRA, 2900, 800);
        near.hp = near.maxHp = 999; // a piercer meets a wall of meat: pass through, don't chew through
        shoot(w, WeaponDef.PIERCE);
        for (int i = 0; i < 60 && !w.bullets.isEmpty(); i++) w.update(0.016f, IDLE);
        assertTrue(near.hp < near.maxHp, "piercer hits the first body");
        assertTrue(far.hp < far.maxHp, "and carries on to the second");
    }

    @Test
    void plainShotStopsAtTheFirstBody() {
        World w = arena(2600, 800);
        put(w, Enemy.Kind.VAKRA, 2700, 800);
        Enemy far = put(w, Enemy.Kind.VAKRA, 2900, 800);
        shoot(w, WeaponDef.SINGLE);
        for (int i = 0; i < 120 && !w.bullets.isEmpty(); i++) w.update(0.016f, IDLE);
        assertEquals(far.maxHp, far.hp, 0.001f, "the body behind the first one is untouched");
    }

    @Test
    void splashTakesOutACrowd() {
        // The mayavi lobs back, so the bomb can be intercepted mid-flight — the duel
        // detonates splash rounds, so it still goes off where it died. The crowd sits at
        // 2850, inside that blast either way; the bystander stays far outside the radius.
        World w = arena(2600, 800);
        Enemy centre = put(w, Enemy.Kind.MAYAVI, 2850, 800);
        put(w, Enemy.Kind.MAYAVI, 2850, 850);
        Enemy bystander = put(w, Enemy.Kind.MAYAVI, 3350, 800);
        while (!centre.dead) {
            shoot(w, WeaponDef.BOMB);
            for (int i = 0; i < 200 && !w.bullets.isEmpty(); i++) w.update(0.016f, IDLE);
        }
        assertTrue(centre.dead, "bomb kills what it lands on");
        assertEquals(bystander.maxHp, bystander.hp, 0.001f, "and spares the far one");
    }

    /** The duel's splash rule: a bomb stopped by enemy fire still explodes where it died. */
    @Test
    void anInterceptedBombStillGoesOff() {
        World w = arena(2600, 800);
        Enemy e = put(w, Enemy.Kind.MAYAVI, 2800, 800);
        w.spawnEnemyBullet(2790, 800, (float) Math.PI, Enemy.GUN[Enemy.Kind.MAYAVI.ordinal()]);
        w.spawnBullet(2644, 800, 0, WeaponDef.BOMB, 1f, 1f);
        for (int i = 0; i < 200 && e.hp == e.maxHp; i++) w.update(0.016f, IDLE);
        assertTrue(e.hp < e.maxHp, "the intercepted bomb still splashed what it died beside");
        for (Bullet b : w.bullets) assertFalse(b.hostile, "and ate the shot that stopped it");
    }

    @Test
    void enemiesShootBack() {
        World w = arena(2600, 800);
        put(w, Enemy.Kind.LANCE, 2600, 1100);
        float hp0 = w.ship.hp;
        boolean fired = false;
        for (int i = 0; i < 400 && w.ship.hp == hp0; i++) {
            w.update(0.016f, IDLE);
            for (Bullet b : w.bullets) if (b.hostile) fired = true;
        }
        assertTrue(fired, "an awake enemy opens fire");
        assertTrue(w.ship.hp < hp0, "and the shot hurts, hull " + w.ship.hp);
    }

    @Test
    void rockStopsEnemyFire() {
        float y = 2600; // Svarna Dvara's middle band
        World w = arena(2600, y); // inside Svarna Dvara, where there is rock to hide behind
        TileMap m = w.tileMap;
        int tx = (int) ((2900 - m.ox) / TileMap.TILE), ty = (int) ((y - m.oy) / TileMap.TILE);
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) m.set(tx + dx, ty + dy, TileMap.ROCK);
        w.spawnEnemyBullet(2750, y, 0); // due east, straight into it
        for (int i = 0; i < 120 && !w.bullets.isEmpty(); i++) w.update(0.016f, IDLE);
        assertTrue(w.bullets.isEmpty(), "enemy fire is stopped by rock");
    }

    @Test
    void aurisOpensFireToo() {
        World w = arena(2600, 800); // the kite used to be the one kind that could never hurt you
        put(w, Enemy.Kind.AURIS, 2600, 1100);
        float hp0 = w.ship.hp;
        boolean fired = false;
        for (int i = 0; i < 600 && w.ship.hp == hp0; i++) {
            w.update(0.016f, IDLE);
            for (Bullet b : w.bullets) if (b.hostile) fired = true;
        }
        assertTrue(fired, "an awake auris opens fire");
        assertTrue(w.ship.hp < hp0, "and the shot hurts");
    }

    /** The volley that first leaves the tube: round count at the firing frame (not a peak
     *  over time — overlapping volleys would stack). */
    private int volleySize(World w, int frames) {
        for (int i = 0; i < frames; i++) {
            w.update(0.016f, IDLE);
            int n = 0;
            for (Bullet b : w.bullets) if (b.hostile) n++;
            if (n > 0) return n;
        }
        return 0;
    }

    /** ATALA FIRES ONE BULLET. Every kind's attack is a single round; the signature is in
     *  the speed, the silhouette and the tell, not in how many arrive. */
    @Test
    void everyKindFiresASingleRound() {
        for (Enemy.Kind k : new Enemy.Kind[] { Enemy.Kind.AURIS, Enemy.Kind.VAKRA,
                Enemy.Kind.BHAVI, Enemy.Kind.MAYAVI, Enemy.Kind.LANCE, Enemy.Kind.HATAKA }) {
            assertEquals(1, Enemy.GUN[k.ordinal()].count, k + " fires one bullet");
            World w = arena(2600, 800);
            put(w, k, 2900, 800);
            assertEquals(1, volleySize(w, 700), k + " puts exactly one round in the air");
        }
    }

    /** Rank grades the one round instead of the count: a senior's single bullet hits harder. */
    @Test
    void rankGradesTheDamageOfTheSingleRound() {
        float base = Enemy.GUN[Enemy.Kind.VAKRA.ordinal()].damage;

        World plain = arena(2600, 800);
        Enemy fod = put(plain, Enemy.Kind.VAKRA, 2900, 800);
        assertEquals(0, fod.rankGrade(), "fodder is grade 0");
        assertEquals(base, volleyDamage(plain, fod), 0.01f, "and its round is the signature's damage");

        World lead = arena(2600, 800);
        Enemy cmd = put(lead, Enemy.Kind.VAKRA, 2900, 800);
        cmd.rank = 1;
        assertEquals(1, cmd.rankGrade());
        assertEquals(base * Enemy.RANK_DAMAGE[1], volleyDamage(lead, cmd), 0.01f,
                "a commander's one round hits harder");

        World boss = arena(2600, 800);
        Enemy el = put(boss, Enemy.Kind.VAKRA, 2900, 800);
        el.elite = true;
        assertEquals(2, el.rankGrade(), "an elite outranks a commander");
        assertEquals(base * Enemy.RANK_DAMAGE[2], volleyDamage(boss, el), 0.01f);
    }

    /** The damage dial and the count are decoupled: one bullet, graded by who is shooting. */
    @Test
    void eachKindStillFiresItsOwnSignature() {
        World w = arena(2600, 800);
        Enemy auris = put(w, Enemy.Kind.AURIS, 2900, 800);
        float aurisDmg = volleyDamage(w, auris);
        w = arena(2600, 800);
        Enemy vakra = put(w, Enemy.Kind.VAKRA, 2900, 800);
        float vakraDmg = volleyDamage(w, vakra);
        assertTrue(aurisDmg != vakraDmg, "auris and vakra still read differently");
        assertTrue(Enemy.GUN[Enemy.Kind.AURIS.ordinal()].speed
                        > Enemy.GUN[Enemy.Kind.VAKRA.ordinal()].speed,
                "auris's round is the faster one");
    }

    /** Waits for the unit's first shot and returns its damage. */
    private float volleyDamage(World w, Enemy e) {
        for (int i = 0; i < 700; i++) {
            w.update(0.016f, IDLE);
            for (Bullet b : w.bullets)
                if (b.hostile) return b.damage;
        }
        throw new AssertionError(e.kind + " never fired");
    }

    @Test
    void bhaviOnlyShootsUpClose() {
        World w = arena(2600, 800);
        put(w, Enemy.Kind.BHAVI, 3200, 800); // 600 px: out of the rusher's range
        assertEquals(0, volleySize(w, 400), "the heavy shell stays cold at range");

        w = arena(2600, 800);
        put(w, Enemy.Kind.BHAVI, 3000, 800); // 400 px: inside it
        assertTrue(volleySize(w, 400) > 0, "and speaks up close");
    }

    @Test
    void mayaviLobMarksItsLanding() {
        World w = arena(2600, 800);
        put(w, Enemy.Kind.MAYAVI, 2900, 800);
        boolean lobbed = false, marked = false;
        for (int i = 0; i < 600 && !lobbed; i++) {
            w.update(0.016f, IDLE);
            for (Bullet b : w.bullets)
                if (b.hostile) { lobbed = true; assertEquals(9f, b.damage, "the lob's damage"); }
            for (Effect e : w.effects)
                if (e.type == Effect.RING) marked = true;
        }
        assertTrue(lobbed, "the lob leaves the tube");
        assertTrue(marked, "the landing is marked before it lands");
    }

    @Test
    void hatakaBetraysOnTheWayDown() {
        World w = arena(2600, 800);
        Enemy e = put(w, Enemy.Kind.HATAKA, 2900, 800);
        e.hurt(24, w); // 45 hp -> 21: under half
        assertEquals(3, volleySize(w, 1), "halfway down it vents its betray ring");
        e.hurt(999, w); // and once more on death
        assertTrue(e.dead, "the kill lands");
        assertTrue(volleySize(w, 1) >= 3, "death vents again");
    }

    @Test
    void woundedUnitsFireFaster() {
        World w = arena(2600, 800);
        Enemy e = put(w, Enemy.Kind.AURIS, 2900, 800); // fireCd 3.0
        float calm = 0, desperate = 0;
        for (int i = 0; i < 600; i++) { // wait out the calm cadence
            w.update(0.016f, IDLE);
            if (!w.bullets.isEmpty()) { calm = e.fireTimer; break; }
        }
        assertTrue(calm > 2.9f, "the calm cadence is the kind's own: " + calm);
        e.hp = e.maxHp * 0.2f; // wounded
        w.bullets.clear();
        for (int i = 0; i < 600; i++) { // wait out the desperate one
            w.update(0.016f, IDLE);
            if (!w.bullets.isEmpty()) { desperate = e.fireTimer; break; }
        }
        assertTrue(desperate < calm * 0.8f, "wounded: faster cadence " + calm + " -> " + desperate);
    }

    @Test
    void lanceFlashesItsSightBeforeTheTap() {
        World w = arena(2600, 800);
        put(w, Enemy.Kind.LANCE, 2900, 800); // fireCd 2.4
        boolean told = false;
        for (int i = 0; i < 600; i++) {
            w.update(0.016f, IDLE);
            for (Bullet b : w.bullets)
                if (b.hostile) { // the tell rides the same frame the tap leaves the tube
                    for (Effect e : w.effects) if (e.type == Effect.FLASH) told = true;
                    assertEquals(9f, b.damage, "the tap's damage");
                    i = 600;
                    break;
                }
        }
        assertTrue(told, "the sentry flashes a laser sight first");
    }

    /** Steps until the player round is gone; returns how much hostile fire survived the trade
     *  (-1 = the trade never resolved inside `frames`). Head-on: the hostile round flies west
     *  at the ship, the player's round flies east from the muzzle. */
    private int afterDuel(World w, WeaponDef gun, WeaponDef shot, int frames) {
        w.spawnEnemyBullet(2822, 800, (float) Math.PI, gun);
        w.spawnBullet(2622, 800, 0, shot, 1f, 1f);
        for (int i = 0; i < frames; i++) {
            w.update(0.016f, IDLE);
            int hostile = 0, friendly = 0;
            for (Bullet b : w.bullets) {
                if (b.hostile) hostile++;
                else friendly++;
            }
            if (friendly == 0) return hostile;
        }
        return -1;
    }

    @Test
    void equalPowerCancelsBothRounds() {
        World w = arena(2600, 800);
        assertEquals(0, afterDuel(w, Enemy.GUN[Enemy.Kind.MAYAVI.ordinal()], WeaponDef.SINGLE, 60),
                "same power: the round and the shot cancel each other");
    }

    @Test
    void aRoundThatBeatsTheShotKillsIt() {
        World w = arena(2600, 800);
        assertEquals(0, afterDuel(w, Enemy.GUN[Enemy.Kind.MAYAVI.ordinal()], WeaponDef.HEAVY, 60),
                "the heavy shell beat the shot — and is spent for it");
    }

    @Test
    void aLightRoundDiesForNothingAndTheShotFliesOn() {
        World w = arena(2600, 800);
        assertEquals(1, afterDuel(w, Enemy.GUN[Enemy.Kind.LANCE.ordinal()], WeaponDef.LIGHT, 60),
                "the MG round couldn't touch it, and the shot flies on");
    }

    /** Cover is a line, not a sample point: rock anywhere along the sight line breaks it. */
    @Test
    void lineOfSightStopsAtARealWall() {
        World w = arena(2600, 800);
        assertTrue(w.clearLine(2600, 800, 3000, 800), "open sky is clear");

        float y = 2600; // Svarna Dvara's middle band
        w = arena(2600, y); // inside Svarna Dvara, where there is rock to hide behind
        TileMap m = w.tileMap;
        int tx = (int) ((2900 - m.ox) / TileMap.TILE), ty = (int) ((y - m.oy) / TileMap.TILE);
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++) m.set(tx + dx, ty + dy, TileMap.ROCK);
        assertFalse(w.clearLine(2600, y, 3200, y), "the wall between breaks the line");
        assertTrue(w.clearLine(2600, y, 2700, y), "stopping short of the wall is still clear");
    }

    @Test
    void shieldStillMattersAgainstLiveFire() {
        World w = arena(2600, 800);
        put(w, Enemy.Kind.LANCE, 2600, 1000);
        w.ship.shieldEnergy = 50;
        float hp0 = w.ship.hp;
        // long enough for the sentry's first shot to arrive, short enough for only that one
        for (int i = 0; i < 140 && w.ship.hp == hp0; i++) w.update(0.016f, controls(true));
        assertEquals(hp0, w.ship.hp, 0.001f, "shield eats the hit");
        assertTrue(w.ship.shieldEnergy < 50, "by draining instead");
    }

    @Test
    void beamScaldsWhatItCrosses() {
        World w = arena(2600, 800);
        Enemy onTheLine = put(w, Enemy.Kind.VAKRA, 3000, 800);
        Enemy offTheLine = put(w, Enemy.Kind.VAKRA, 3000, 1000);
        w.laser(2622, 800, 0, 900, 1, 1, 1, 30);
        assertTrue(onTheLine.hp < onTheLine.maxHp, "the beam hits what is in front of it");
        assertEquals(offTheLine.maxHp, offTheLine.hp, 0.001f, "and misses what is not");
    }

    /** Dash ghosts the ship: the shot keeps flying and the hull never feels it. */
    @Test
    void dashLetsBulletsPassThrough() {
        World w = arena(2600, 800);
        float hp0 = w.ship.hp;
        w.ship.iFrames = 0.4f; // what a dash grants
        w.spawnEnemyBullet(2600, 800, 0);
        w.update(0.016f, IDLE);
        assertEquals(1, w.bullets.size(), "the shot flies on through");
        assertEquals(hp0, w.ship.hp, 0.001f, "and the hull never feels it");

        World plain = arena(2600, 800);
        float hp1 = plain.ship.hp;
        plain.spawnEnemyBullet(2600, 800, 0);
        plain.update(0.016f, IDLE);
        assertTrue(plain.bullets.isEmpty(), "without the dash the shot is absorbed");
        assertTrue(plain.ship.hp < hp1, "and it hurts");
    }

    /** Every pull and every wound leaves its mark: muzzle flash, sparks, spray, debris. */
    @Test
    void sparksFlyOffEveryHit() {
        World w = arena(2600, 800);
        Enemy e = put(w, Enemy.Kind.VAKRA, 2650, 800);
        w.spawnBullet(2600, 800, 0, WeaponDef.SINGLE, 1f, 1f);
        assertFalse(w.effects.isEmpty(), "the muzzle flashes on the pull");
        w.effects.clear();
        e.hurt(1, w);
        assertFalse(w.effects.isEmpty(), "a wound throws sparks");
        w.effects.clear();
        w.bullets.get(0).detonate(w);
        assertFalse(w.effects.isEmpty(), "the spent shot sprays along its path");
        w.effects.clear();
        e.hurt(999, w);
        assertFalse(w.effects.isEmpty(), "and the kill throws debris");
    }

    /** Full Atala, ship flying the descent with the trigger down: nothing may throw, leak or wedge. */
    @Test
    void longDescentHoldsTogether() {
        World w = new World();
        ShipControls down = new ShipControls() {
            public float thrustX() { return 0; }
            public float thrustY() { return 1; }
            public float rotate() { return 0; }
            public boolean fire() { return true; }
            public boolean boost() { return true; }
            public boolean dash() { return false; }
            public boolean shield() { return false; }
            public boolean mouseAim() { return false; }
            public float aimX() { return 0; }
            public float aimY() { return 0; }
        };
        int start = w.enemies.size();
        float[] stops = { 800, 2600, 4800, 7000, 9500, 12000, 14200 };
        for (int i = 0; i < 3600; i++) {
            if (i % 400 == 0) { // hop the descent so every region gets fought in
                w.ship.x = 2600;
                w.ship.y = stops[(i / 400) % stops.length];
                w.ship.invuln = 2f;
            }
            w.ship.facing = (float) Math.PI / 2; // nose north
            w.update(1 / 60f, down);
            assertTrue(w.ship.x >= 0 && w.ship.x <= w.width, "ship stayed in the world");
            assertTrue(w.ship.y >= 0 && w.ship.y <= w.height, "ship stayed in the world");
            assertTrue(w.bullets.size() <= 400, "bullet cap held");
        }
        assertTrue(w.enemies.size() < start, "the descent kills something: " + start + " -> " + w.enemies.size());
        for (Enemy e : w.enemies) assertFalse(e.dead, "no corpses left in the roster");
    }
}