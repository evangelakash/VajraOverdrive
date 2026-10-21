package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vajra.enemy.Enemy;

/** The loader probes idle_0, idle_1, ... until a file is missing, so the frames on disk have to be
 *  contiguous — and the animation has to reach every one of them. */
class EnemyArtTest {
    @Test
    void everyEnemyShipsContiguousIdleFrames() {
        File root = enemyRoot();
        assertNotNull(root, "no assets/atala/enemies from " + new File(".").getAbsolutePath());
        for (Enemy.Kind k : Enemy.Kind.values()) {
            File dir = new File(root, k.name().toLowerCase());
            assertTrue(dir.isDirectory(), "no folder for " + k);
            File[] idle = dir.listFiles((d, n) -> n.startsWith("idle_") && n.endsWith(".png"));
            assertNotNull(idle, "cannot list " + dir);
            int contiguous = 0;
            while (new File(dir, "idle_" + contiguous + ".png").isFile()) contiguous++;
            assertTrue(contiguous >= 1, k + " has no idle_0.png");
            assertEquals(idle.length, contiguous,
                    k + " has a gap in idle_N — the loader would stop early and drop frames");
        }
    }

    @Test
    void animationReachesEveryFrame() {
        for (int n = 1; n <= 6; n++) {
            Set<Integer> seen = new HashSet<>();
            for (int t = 0; t < 400; t++) {
                int f = EnemyImages.frame(t / 20f, 0f, n);
                assertTrue(f >= 0 && f < n, "frame out of range for " + n + " frames");
                seen.add(f);
                seen.add(EnemyImages.frame(t / 20f, 2.5f, n));
            }
            assertEquals(n, seen.size(), "animation must touch all " + n + " frames");
        }
    }

    private static File enemyRoot() {
        for (String p : new String[] { "assets/atala/enemies", "../assets/atala/enemies" }) {
            File d = new File(p);
            if (d.isDirectory()) return d;
        }
        return null;
    }
}
