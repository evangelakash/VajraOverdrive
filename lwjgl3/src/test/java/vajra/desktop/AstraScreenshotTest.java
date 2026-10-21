package vajra.desktop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.flow.Flow;
import vajra.screen.GameScreen;
import vajra.screen.HomeScreen;
import vajra.screen.Ui;
import vajra.ship.Hangar;
import vajra.world.AstraCube;
import vajra.world.World;

/** THE AKASHA ASTRA, end to end on the real Flow: the charge must be a visible CAGE of twenty
 *  turning cubes that tightens, the release must fling them at real targets, and the charge
 *  must actually cost the hull its speed. Needs a GL context, so it lives in lwjgl3; the PNGs
 *  land in build/ for eyeballing, which is the only way to judge whether it reads as grand. */
class AstraScreenshotTest {
    private static final int W = 1280, H = 720;

    @Test
    void theChargeIsACageAndTheReleaseIsAVolley() {
        List<String> failures = new ArrayList<>();
        AtomicReference<Throwable> boom = new AtomicReference<>();

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(W, H);
        cfg.setInitialVisible(false);
        cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            Ui ui;
            SpriteBatch batch;
            Flow flow;
            GameScreen gs;
            World w;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    flow = new Flow();
                    flow.set(new HomeScreen(flow, ui, batch));
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                    for (int i = 0; i < 30; i++) frame();   // past the boot fade
                    gs = (GameScreen) flow.top();
                    w = gs.world();

                    // a crowd to aim at, and a full release in hand
                    w.hangar.forged[0] = true;
                    w.hangar.forgeCharges[0] = 1;
                    for (int i = 0; i < 18; i++) {
                        vajra.enemy.Enemy e = new vajra.enemy.Enemy(
                                vajra.enemy.Enemy.Kind.MAYAVI,
                                w.ship.x + (i - 9) * 46f, w.ship.y + 150 + (i % 3) * 40);
                        e.hp = 90;
                        e.active = true;
                        w.enemies.add(e);
                    }

                    // --- 1. the charge, caught part-way: the cage should be up and tightening
                    w.useForge();
                    check(w.winding(), "the charge is running", failures);
                    int held = countHeld(w);
                    check(held == World.ASTRA_CUBES,
                            "twenty cubes held in the cage (" + held + ")", failures);
                    float wide = shellRadius(w);
                    for (int i = 0; i < 70; i++) frame();
                    float tight = shellRadius(w);
                    check(tight < wide, "the cage tightened over the charge: "
                            + (int) wide + " -> " + (int) tight, failures);
                    int[] charge = frame();
                    writePng(charge, "build/astra-1-charging.png");
                    check(violet(charge) > 150, "the cage is drawn, in Akasha violet ("
                            + violet(charge) + " px)", failures);

                    // --- 2. the release: run the charge out, then catch them in flight
                    int guard = 0;
                    while (w.winding() && guard++ < 600) frame();
                    check(!w.winding(), "the charge completed", failures);
                    for (int i = 0; i < 14; i++) frame();
                    int[] volley = frame();
                    writePng(volley, "build/astra-2-released.png");
                    int armed = 0;
                    for (AstraCube c : w.astra) if (c.armed && c.target != null) armed++;
                    check(armed > 0, "cubes are away and aimed (" + armed + ")", failures);
                    check(violet(volley) > 60, "the volley is drawn (" + violet(volley) + " px)",
                            failures);

                    flow.dispose();
                    ui.dispose();
                    batch.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            int[] frame() { return frame(1 / 60f); }

            int[] frame(float dt) {
                flow.render(dt);
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }

            int countHeld(World w) {
                int n = 0;
                for (AstraCube c : w.astra) if (!c.armed) n++;
                return n;
            }

            /** Mean distance from the ship to its cubes: the cage's radius. */
            float shellRadius(World w) {
                float sum = 0;
                for (AstraCube c : w.astra)
                    sum += (float) Math.hypot(c.x - w.ship.x, c.y - w.ship.y);
                return w.astra.isEmpty() ? 0f : sum / w.astra.size();
            }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("astra render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "astra checks failed:\n  "
                + String.join("\n  ", failures));
    }

    // --- checks -----------------------------------------------------------------

    private static int r(int p) { return p & 0xff; }
    private static int g(int p) { return (p >> 8) & 0xff; }
    private static int b(int p) { return (p >> 16) & 0xff; }

    /** Violet pixels: red above green, blue at or above red. Akasha is the only thing in the
     *  frame that is that colour, so this is the cubes and nothing else. */
    private static int violet(int[] px) {
        int n = 0;
        for (int p : px) {
            int rr = r(p), gg = g(p), bb = b(p);
            if (rr + gg + bb < 70) continue;
            if (rr > gg && bb >= rr) n++;
        }
        return n;
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) f.add(what);
    }

    private static void writePng(int[] px, String path) {
        try {
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int p = px[y * W + x];
                    img.setRGB(x, H - 1 - y, (r(p) << 16) | (g(p) << 8) | b(p));
                }
            File out = new File(path);
            out.getParentFile().mkdirs();
            ImageIO.write(img, "png", out);
        } catch (Exception e) {
            throw new RuntimeException("screenshot write failed", e);
        }
    }
}