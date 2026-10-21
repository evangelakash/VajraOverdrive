package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.Ship;
import vajra.ship.ShipControls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player–enemy contact shoves both apart along the contact line. */
class WorldContactTest {
    private static final ShipControls NONE = new ShipControls() {
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

    private static World worldAt(float x) {
        World w = new World();
        w.enemies.clear();
        w.ship.x = x;
        w.ship.y = 800;
        w.ship.vx = 0;
        w.ship.vy = 0;
        return w;
    }

    @Test
    void contactShovesShipAwayAndTheySeparate() {
        World w = worldAt(1000);
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 1030, 800); // 30px apart: boxes overlap
        w.enemies.add(e);
        w.update(0.016f, NONE);
        // Ship impulse lands after Ship.update this frame, so it is exactly the raw kick:
        // enemy sits east, ship is shoved west along the contact line.
        assertEquals(-World.KNOCK, w.ship.vx, 0.5f);
        assertEquals(0, w.ship.vy, 3f); // the enemy's idle orbit tilts the contact line a hair
        // Enemy got the equal and opposite kick: relative velocity points apart.
        assertTrue(w.ship.vx < e.vx, "expected separation, got ship=" + w.ship.vx + " enemy=" + e.vx);
    }

    @Test
    void noContactNoShove() {
        World w = worldAt(1000);
        w.enemies.add(new Enemy(Enemy.Kind.MAYAVI, 1200, 800)); // 200px apart: no overlap
        w.update(0.016f, NONE);
        assertEquals(0, w.ship.vx, 0.5f);
        assertEquals(0, w.ship.vy, 0.5f);
    }

    @Test
    void contactRamsEnemyWithCooldown() {
        World w = worldAt(1000);
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 1030, 800);
        w.enemies.add(e);
        float hp0 = e.hp;
        w.update(0.016f, NONE);
        assertEquals(hp0 - World.RAM, e.hp, 0.5f, "the ram takes a bite");
        assertTrue(e.recover > 1.5f, "knockback opens the 2s return-home window");
        // pin them together again: the cooldown must hold the second bite back
        w.ship.x = 1000;
        w.ship.y = 800;
        w.ship.vx = 0;
        w.ship.vy = 0;
        e.x = 1030;
        e.y = 800;
        e.vx = 0;
        e.vy = 0;
        w.update(0.016f, NONE);
        assertEquals(hp0 - World.RAM, e.hp, 0.5f, "one ram per half second");
    }

    @Test
    void hpBarDrainsSmoothlyToTheNewValue() {
        World w = worldAt(1000);
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 1030, 800);
        w.enemies.add(e);
        assertEquals(e.hp, e.dispHp, 0.01f, "bar starts at full");
        e.hurt(30, w);
        assertEquals(e.maxHp, e.dispHp, 0.01f, "bar does not snap on the hit");
        assertTrue(e.dispHp > e.hp, "it leads while draining");
        for (int i = 0; i < 60; i++) e.update(0.016f, w, i * 0.016f); // ~1s
        assertEquals(e.hp, e.dispHp, 1f, "eases onto the real value");
    }
}
