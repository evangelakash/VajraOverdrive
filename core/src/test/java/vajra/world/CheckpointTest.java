package vajra.world;

import vajra.ship.Ship;
import vajra.ship.ShipControls;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Checkpoints: one per Atala band (Bhu Loka has none), touching the pink collector spends it,
 *  the most recent one collected is the respawn anchor, and lives run out into game over. */
class CheckpointTest {
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

    @Test
    void eachRegionGetsOneOnOpenGround() {
        World w = new World();
        assertEquals(5, w.checkpoints.length, "one per Atala band, none in Bhu Loka");
        for (int i = 0; i < w.checkpoints.length; i++) {
            Checkpoint c = w.checkpoints[i];
            assertNotNull(c, "band " + (i + 1) + " checkpoint");
            int r = i + 1;
            assertTrue(c.y > w.regionStart[r] && c.y < w.regionEnd[r], "stays in its band");
            assertFalse(w.blockedPx(c.x, c.y), "not in rock at " + c.x + "," + c.y);
            assertFalse(w.blockedPx(c.x + 40, c.y), "room around it");
            assertFalse(w.blockedPx(c.x - 40, c.y));
            assertFalse(w.blockedPx(c.x, c.y + 40));
            assertFalse(w.blockedPx(c.x, c.y - 40));
        }
    }

    @Test
    void touchingTheCollectorSpendsIt() {
        World w = new World();
        Checkpoint c = w.checkpoints[0];
        w.ship.x = c.x;
        w.ship.y = c.y;
        w.update(0.016f, NONE);
        assertTrue(c.collected, "touching orb2 spends the checkpoint");
        assertSame(c, w.respawnCp, "and it becomes the respawn anchor");
        assertFalse(w.effects.isEmpty(), "the take-off pops a pink ring");
    }

    @Test
    void deathAnchorsAtMostRecentCheckpoint() {
        World w = new World();
        Checkpoint c = w.checkpoints[0];
        w.ship.x = c.x;
        w.ship.y = c.y;
        w.update(0.016f, NONE);
        w.ship.x = w.width / 2;
        w.ship.y = 4000;
        w.ship.hp = 1;
        w.ship.invuln = 0;
        w.ship.damage(999, w);
        assertEquals(c.x, w.ship.x, 0.001f, "back at the checkpoint, not the entrance");
        assertEquals(c.y, w.ship.y, 0.001f);
        assertEquals(2, w.lives, "the death cost a life");
        assertEquals(w.ship.maxHp(), w.ship.hp, 0.001f, "full hull on respawn");
    }

    @Test
    void allLivesSpentEndsTheRun() {
        World w = new World();
        Ship s = w.ship;
        for (int i = 0; i < 2; i++) {
            s.hp = 1;
            s.invuln = 0;
            s.damage(999, w);
            assertFalse(w.gameOver, "lives remain after death " + (i + 1));
        }
        s.hp = 1;
        s.invuln = 0;
        s.damage(999, w);
        assertTrue(w.gameOver, "third death empties the last life");
        assertEquals(0, w.lives);
        assertEquals(0f, s.hp, 0.001f, "the hull stays where it fell");
        s.invuln = 0;
        s.damage(999, w);
        assertEquals(0, w.lives, "no damage after game over");
        assertFalse(w.gameOver && s.hp > 0, "hull stays at zero");
    }
}
