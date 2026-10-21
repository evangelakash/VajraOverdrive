package vajra.enemy;

import vajra.world.World;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Drives Enemy.update, not Brain.think: the brain only writes velocity, integration is the enemy's job. */
class BrainTest {
    /** Empty arena with the ship parked far away, so only the rule under test moves things. */
    private World bare() {
        World w = new World();
        w.enemies.clear();
        w.ship.x = 2600;
        w.ship.y = 5000;
        return w;
    }

    @Test
    void shiesFromWalls() {
        World w = new World();
        Enemy anchor = null;
        for (Enemy e : w.enemies) {
            if (e.kind == Enemy.Kind.AURIS) {
                anchor = e;
                break;
            }
        }
        assertNotNull(anchor);
        Enemy e = new Enemy(Enemy.Kind.AURIS, anchor.homeX, anchor.homeY);
        e.active = true;
        e.patrolAmp = 0;
        w.ship.x = e.homeX + 1900;
        w.ship.y = e.homeY;
        for (int i = 0; i < 60; i++) {
            e.update(0.016f, w, i * 0.016f);
            assertFalse(w.tileMap.solidPx(e.x, e.y), "brain must never enter rock, step " + i);
        }
        assertTrue(e.x > e.homeX - 200, "whiskers keep it out of deep rock");
    }

    @Test
    void matesKeepDistance() {
        World w = bare();
        Enemy a = new Enemy(Enemy.Kind.AURIS, 2600, 2600);
        Enemy b = new Enemy(Enemy.Kind.AURIS, 2610, 2600);
        a.active = true;
        b.active = true;
        a.patrolAmp = 0;
        b.patrolAmp = 0;
        w.enemies.add(a);
        w.enemies.add(b);
        float d0 = Math.abs(a.x - b.x);
        for (int i = 0; i < 30; i++) a.update(0.016f, w, i * 0.016f);
        assertTrue(Math.abs(a.x - b.x) > d0, "separation should push mates apart");
    }

    @Test
    void positionPersistsSoEnemiesCanActuallyTravel() {
        World w = bare();
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 2600, 2600);
        e.active = true;
        w.enemies.add(e);
        float x0 = e.x, y0 = e.y;
        for (int i = 0; i < 120; i++) e.update(0.016f, w, i * 0.016f);
        float moved = (float) Math.hypot(e.x - x0, e.y - y0);
        assertTrue(moved > 20, "orbit should carry it off its home, moved " + moved);
    }

    @Test
    void aurisKitesAndFacesPlayer() {
        World w = bare();
        Enemy e = new Enemy(Enemy.Kind.AURIS, 2600, 2600);
        e.active = true;
        e.patrolAmp = 0;
        w.enemies.add(e);
        w.ship.x = 2650;
        w.ship.y = 2600;
        float x0 = e.x;
        for (int i = 0; i < 20; i++) e.update(0.016f, w, i * 0.016f);
        assertTrue(e.x < x0, "auris should back away from a close player");
        assertEquals(0f, e.facing, 0.2f, "facing should track the player (east = 0)");
    }

    @Test
    void routFollowsDeadCommander() {
        World w = bare();
        Enemy cmd = new Enemy(Enemy.Kind.VAKRA, 2600, 2600);
        Enemy e = new Enemy(Enemy.Kind.AURIS, 2650, 2600);
        e.commander = cmd;
        e.active = true;
        cmd.dead = true; // the only thing that should ever set routed
        float x0 = e.x;
        for (int i = 0; i < 10; i++) e.update(0.016f, w, 0f);
        assertTrue(e.routed, "a dead commander routs its followers");
        assertTrue(e.x > x0, "rout flees away from the commander");
    }

    /** FORMATIONS: flee 2.5s, then regroup — no senior in sight, so back to normal lone behaviour. */
    @Test
    void routWithNoSeniorNearGoesBackToNormal() {
        World w = bare(); // ship parked at (2600,5000): out of fire range and the finding radius
        Enemy cmd = new Enemy(Enemy.Kind.VAKRA, 2600, 800);
        Enemy e = new Enemy(Enemy.Kind.AURIS, 2650, 800);
        e.commander = cmd;
        e.active = true;
        w.enemies.add(cmd); // alive at first: e rides the formation and adopts cmd's post
        for (int i = 0; i < 10; i++) {
            e.update(1 / 60f, w, 0f);
            cmd.update(1 / 60f, w, 0f);
        }
        assertEquals(2600, e.homeX, 0.5f, "a follower's post tracks its leader");
        cmd.dead = true;
        for (int i = 0; i < 45; i++) e.update(1 / 60f, w, 0f); // 0.75s of running
        float fleeing = (float) Math.hypot(e.vx, e.vy);
        assertTrue(fleeing > 130, "rout runs faster than cruise (115), got " + fleeing);
        assertTrue(e.routed, "still fleeing while rout lasts");
        for (int i = 0; i < 205; i++) e.update(1 / 60f, w, 0f); // to 4.1s: rout expired
        assertFalse(e.routed, "rout ends and the unit rejoins normal behaviour");
        assertNull(e.commander, "no senior in sight: it follows its own post again");
        assertEquals(2650, e.homeX, 0.5f, "own spawn post restored, not the dead leader's");
        for (int i = 0; i < 180; i++) e.update(1 / 60f, w, 0f); // settle on its own post
        assertTrue(Math.hypot(e.x - e.homeX, e.y - e.homeY) < 200, "back at its own post");
        assertTrue(w.bullets.isEmpty(), "a lone unit out of range does not shoot");
    }

    /** A unit caught dead on its post must still break away — the flee vector can't vanish there. */
    @Test
    void routOnThePostStillFlees() {
        World w = bare(); // ship parked at (2600,5000): due south of the post
        Enemy cmd = new Enemy(Enemy.Kind.VAKRA, 2600, 800);
        Enemy e = new Enemy(Enemy.Kind.AURIS, 2600, 800); // standing exactly on the leader's post
        e.commander = cmd;
        e.active = true;
        cmd.dead = true;
        for (int i = 0; i < 45; i++) e.update(1 / 60f, w, 0f);
        float fleeing = (float) Math.hypot(e.vx, e.vy);
        assertTrue(fleeing > 130, "no freeze on the post: rout still runs, got " + fleeing);
        assertTrue(e.y < 800, "and breaks away from the player to the south");
    }

    /** A rout that already ran once must still flee: routAge restarts with every new rout. */
    @Test
    void secondRoutFleesAgain() {
        World w = bare();
        Enemy cmd = new Enemy(Enemy.Kind.VAKRA, 2600, 800);
        Enemy e = new Enemy(Enemy.Kind.AURIS, 2650, 800);
        e.commander = cmd;
        e.active = true;
        e.routAge = 99; // stale age left over from a rout long ago
        cmd.dead = true;
        e.update(1 / 60f, w, 0f);
        assertTrue(e.routed, "a fresh rout starts even with a stale routAge");
        assertTrue(e.routAge <= 1 / 60f + 0.001f, "the age restarts with the rout, got " + e.routAge);
    }

    /** WOUND RESPONSE: a beaten HATAKA gives up its perch and backs away. */
    @Test
    void woundedHatakaBreaksCover() {
        World w = bare();
        Enemy e = new Enemy(Enemy.Kind.HATAKA, 2600, 2600);
        e.active = true;
        e.patrolAmp = 0;
        w.enemies.add(e);
        w.ship.x = 2800; // east, inside the 500px wound range
        w.ship.y = 2600;
        e.hp = e.maxHp * 0.2f;
        float x0 = e.x;
        for (int i = 0; i < 30; i++) e.update(1 / 60f, w, i / 60f);
        assertTrue(e.x < x0, "wounded hataka backs away from the player");
    }

    /** FORMATIONS: after rout a follower grabs the nearest living higher rank inside FIND. */
    @Test
    void routJoinsSeniorWithinFindingRadius() {
        World w = bare();
        Enemy old = new Enemy(Enemy.Kind.VAKRA, 2600, 800);
        old.dead = true;
        Enemy lead = new Enemy(Enemy.Kind.MAYAVI, 3000, 800);
        lead.rank = 1;
        lead.active = true;
        Enemy e = new Enemy(Enemy.Kind.AURIS, 2650, 800);
        e.commander = old;
        e.active = true;
        w.enemies.add(old);
        w.enemies.add(lead);
        w.enemies.add(e);
        for (int i = 0; i < 170; i++) e.update(1 / 60f, w, i / 60f); // 2.8s > 2.5s rout
        assertFalse(e.routed, "rout ends by regrouping");
        assertSame(lead, e.commander, "joined the senior inside the finding radius");
        assertEquals(lead.homeX, e.homeX, 0.5f, "post moves to the new senior");
        for (int i = 0; i < 180; i++) {
            for (Enemy o : w.enemies) o.update(1 / 60f, w, i / 60f);
        }
        float d = (float) Math.hypot(e.x - lead.x, e.y - lead.y);
        assertTrue(d < 200, "settled into the formation ring, distance " + d);
    }

    /** FORMATIONS: slots are spread by the living member count — two members sit apart. */
    @Test
    void formationSpreadsSlotsByCount() {
        World w = bare();
        Enemy cmd = new Enemy(Enemy.Kind.VAKRA, 2600, 2600);
        cmd.rank = 1;
        cmd.active = true;
        Enemy a = new Enemy(Enemy.Kind.AURIS, 2640, 2600);
        Enemy b = new Enemy(Enemy.Kind.AURIS, 2660, 2600);
        a.commander = cmd;
        b.commander = cmd;
        a.active = true;
        b.active = true;
        w.enemies.add(cmd);
        w.enemies.add(a);
        w.enemies.add(b);
        for (int i = 0; i < 240; i++) for (Enemy o : w.enemies) o.update(1 / 60f, w, i / 60f);
        float d = (float) Math.hypot(a.x - b.x, a.y - b.y);
        assertTrue(d > 100, "two members take opposite slots, got " + d);
    }
}