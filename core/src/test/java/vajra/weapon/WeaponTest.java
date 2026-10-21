package vajra.weapon;

import vajra.enemy.Enemy;
import vajra.ship.Ship;
import vajra.ship.ShipControls;
import vajra.ship.ShipDef;
import vajra.world.Effect;
import vajra.world.TileMap;
import vajra.world.World;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WeaponTest {
    private static ShipControls of(final boolean fire) {
        return new ShipControls() {
            public float thrustX() { return 0; }
            public float thrustY() { return 0; }
            public float rotate() { return 0; }
            public boolean fire() { return fire; }
            public boolean boost() { return false; }
            public boolean dash() { return false; }
            public boolean shield() { return false; }
            public boolean mouseAim() { return false; }
            public float aimX() { return 0; }
            public float aimY() { return 0; }
        };
    }

    private static final ShipControls HELD = of(true);
    private static final ShipControls IDLE = of(false);

    private void step(World w, float seconds) {
        int n = (int) (seconds / 0.016f);
        for (int i = 0; i < n; i++) w.update(0.016f, HELD);
    }

    @Test
    void singleFiresOneBulletPerCooldown() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.SINGLE;
        step(w, 0.02f);
        assertEquals(1, w.bullets.size());
        step(w, 0.2f); // still inside the 0.25s cooldown
        assertEquals(1, w.bullets.size(), "cooldown gates the next shot");
        step(w, 0.3f);
        assertTrue(w.bullets.size() >= 2, "fires again after cooldown");
    }

    @Test
    void doubleFiresASpreadPair() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.DOUBLE;
        step(w, 0.02f);
        assertEquals(2, w.bullets.size());
        Bullet a = w.bullets.get(0), b = w.bullets.get(1);
        float aa = (float) Math.atan2(a.vy, a.vx), ba = (float) Math.atan2(b.vy, b.vx);
        assertTrue(aa < 0 && ba > 0, "pair straddles the aim line");
        assertEquals((float) Math.toRadians(10), Math.abs(aa - ba), 0.01f);
    }

    @Test
    void buckshotFansFiveAcrossTheSpread() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.SPREAD;
        step(w, 0.02f);
        assertEquals(5, w.bullets.size());
        float min = 10, max = -10;
        for (Bullet b : w.bullets) {
            float a = (float) Math.toDegrees(Math.atan2(b.vy, b.vx));
            min = Math.min(min, a);
            max = Math.max(max, a);
        }
        assertEquals(45f, max - min, 1f, "fan spans the spread");
    }

    @Test
    void burstSchedulesFollowUpShots() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.BURST1;
        step(w, 0.02f);
        assertEquals(1, w.bullets.size(), "first burst shot fires immediately");
        step(w, 0.2f); // two 0.09s gaps elapse
        assertEquals(3, w.bullets.size(), "burst completes");
        step(w, 0.2f); // still holding: a re-armed burst would keep firing every gap
        assertEquals(3, w.bullets.size(), "and stops — no endless burst");
    }

    @Test
    void damageUpgradeDoesNotChangeBulletSpeed() {
        World w = new World();
        w.hangar.coins = 9999; // test purse: the yard ships with 0
        ShipDef d = ShipDef.ALL[3];
        w.ship.setDef(d);
        w.hangar.buyShip(3, 1); // a tier upgrade needs the hull bought first
        w.hangar.shards[0] = vajra.ship.Hangar.SHARD_COST; // x.3 wants shards on top of coins
        w.ship.hangar.buy(d, 2); // Seeker Chips: ×1.35 damage, no speed stat
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.SINGLE;
        step(w, 0.02f);
        Bullet b = w.bullets.get(0);
        float sp = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy);
        assertEquals(WeaponDef.SINGLE.speed, sp, 0.5f, "a damage tier must not move the bullet");
        assertEquals(WeaponDef.SINGLE.damage * 1.35f, b.damage, 0.01f, "the tier lands on damage");
    }

    @Test
    void chargeBuildsAndReleasesAtPower() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.CHARGE;
        step(w, 0.8f); // hold to full charge
        assertEquals(0, w.bullets.size(), "charge weapons wait for release");
        w.ship.update(0.016f, IDLE, w); // release
        assertEquals(1, w.bullets.size());
        assertTrue(w.bullets.get(0).damage > WeaponDef.CHARGE.damage, "charged shot hits harder");
    }

    @Test
    void chargeReleaseRespectsCooldown() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.CHARGE;
        step(w, 0.2f);                  // hold past the 0.12s minimum
        w.ship.update(0.016f, IDLE, w); // release: shot 1
        assertEquals(1, w.bullets.size());
        step(w, 0.15f);                 // hold again: charge above the minimum, cooldown still on
        w.ship.update(0.016f, IDLE, w); // release inside the 0.2s cooldown
        assertEquals(1, w.bullets.size(), "tap-firing cannot outrun the cooldown");
        step(w, 0.25f);                 // cooldown elapses while held
        w.ship.update(0.016f, IDLE, w);
        assertTrue(w.bullets.size() >= 2, "fires again after the cooldown");
    }

    @Test
    void laserBeamsInsteadOfProjectiles() {
        World w = new World();
        w.ship.facing = 0;
        w.ship.weapon.def = WeaponDef.LASER;
        step(w, 0.02f);
        assertEquals(0, w.bullets.size(), "beam makes no projectile");
        boolean flash = false;
        for (Effect e : w.effects) {
            if (e.type != Effect.FLASH) continue;
            flash = true;
            float len = (float) Math.sqrt((e.x2 - e.x) * (e.x2 - e.x) + (e.y2 - e.y) * (e.y2 - e.y));
            assertTrue(len <= WeaponDef.LASER.range + 1, "beam respects range");
        }
        assertTrue(flash, "beam draws a flash line");
    }

    @Test
    void missilesCurveTowardEnemies() {
        World w = new World();
        w.enemies.clear(); // bullets hit things now: give the missile one deliberate target
        Enemy target = new Enemy(Enemy.Kind.VAKRA, 3000, 2600);
        w.enemies.add(target);
        Ship s = w.ship;
        s.weapon.def = WeaponDef.MISSILE;
        s.x = 2600;
        s.y = 2600;
        s.facing = 0;
        step(w, 0.02f);
        assertEquals(1, w.bullets.size());
        float a0 = (float) Math.atan2(w.bullets.get(0).vy, w.bullets.get(0).vx);
        step(w, 0.2f);
        if (w.bullets.isEmpty()) fail("missile died before curving");
        float a1 = (float) Math.atan2(w.bullets.get(0).vy, w.bullets.get(0).vx);
        assertNotEquals(a0, a1, 0.001f, "homing turns the missile");
    }

    @Test
    void wallborePassesThroughRock() {
        World w = new World();
        w.enemies.clear();
        Ship s = w.ship;
        s.weapon.def = WeaponDef.THROUGH;
        s.x = 2600;
        s.y = 2600; // Svarna Dvara's middle band
        TileMap m = w.tileMap;
        int ctx = (int) ((s.x - m.ox) / TileMap.TILE);
        int cty = (int) ((s.y - m.oy) / TileMap.TILE);
        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, 1 }, { 0, -1 } };
        float facing = 0;
        float wallDist = -1;
        for (int[] d : dirs) {
            for (int n = 1; n < 80; n++) {
                if (m.get(ctx + d[0] * n, cty + d[1] * n) != TileMap.EMPTY) {
                    facing = (float) Math.atan2(d[1], d[0]);
                    wallDist = n * TileMap.TILE;
                    break;
                }
            }
            if (wallDist > 0) break;
        }
        assertTrue(wallDist > 0, "needs a wall in some direction from the ship");
        s.facing = facing;
        step(w, 0.02f);
        boolean crossed = false;
        for (int i = 0; i < 600 && !w.bullets.isEmpty(); i++) {
            w.update(0.016f, IDLE);
            if (w.bullets.isEmpty()) break; // died before crossing
            Bullet b = w.bullets.get(0);
            if (Math.sqrt((b.x - s.x) * (b.x - s.x) + (b.y - s.y) * (b.y - s.y)) > wallDist + 32) {
                crossed = true;
                break;
            }
        }
        assertTrue(crossed, "bullet must be alive past the wall column (pierceWalls)");
    }

    @Test
    void bombExplodesIntoARing() {
        World w = new World();
        w.enemies.clear();
        Ship s = w.ship;
        s.weapon.def = WeaponDef.BOMB;
        s.x = 2600;
        s.y = 2600;
        s.facing = 0;
        step(w, 0.02f);
        assertEquals(1, w.bullets.size());
        int rings0 = countRings(w);
        for (int i = 0; i < 300 && w.bullets.size() > 0; i++) w.update(0.016f, IDLE);
        assertTrue(countRings(w) > rings0, "bomb leaves a blast ring on impact");
    }

    private int countRings(World w) {
        int n = 0;
        for (Effect e : w.effects) if (e.type == Effect.RING) n++;
        return n;
    }
}
