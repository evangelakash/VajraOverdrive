package vajra.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OrbTest {
    @Test
    void cyclesHiddenVisibleHidden() {
        World w = new World();
        Orb o = new Orb(2600, 800, 0.5f);
        assertFalse(o.visible);
        o.update(0.6f, w, new Random(1));
        assertTrue(o.visible);
        assertTrue(o.scale == 1f || o.scale == 1.5f || o.scale == 2f);
        o.update(2.4f, w, new Random(1));
        assertFalse(o.visible);
    }

    @Test
    void spinsAndAnimatesWhileVisible() {
        World w = new World();
        Orb o = new Orb(2600, 800, 0f);
        o.update(0.01f, w, new Random(1));
        float r0 = o.rotation;
        o.update(0.5f, w, new Random(1));
        assertTrue(o.rotation != r0);
        assertTrue(o.frameIndex(8) >= 0 && o.frameIndex(8) < 8);
        assertTrue(o.alpha() >= 0f && o.alpha() <= 1f);
    }

    @Test
    void openSpotsAvoidWalls() {
        World w = new World();
        Random rng = new Random(3);
        for (int i = 0; i < 20; i++) {
            float[] s = w.randomOpenSpot(rng);
            assertFalse(w.tileMap.solidPx(s[0], s[1]), "spot in wall at " + s[0] + "," + s[1]);
            assertFalse(w.tileMapR2.solidPx(s[0], s[1]), "spot in wall at " + s[0] + "," + s[1]);
        }
    }
}
