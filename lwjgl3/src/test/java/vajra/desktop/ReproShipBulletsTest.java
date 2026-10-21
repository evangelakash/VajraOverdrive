package vajra.desktop;

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
import javax.imageio.ImageIO;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import vajra.flow.Flow;
import vajra.screen.GameScreen;
import vajra.screen.HomeScreen;
import vajra.screen.Ui;
import vajra.weapon.WeaponDef;
import vajra.world.World;

/** Repro of the "ship and bullets missing, only circles visible" report at the
 *  user's home resolution (1920x1080): boot the real Flow, enter the run, spawn
 *  live rounds, capture the frame and sample the pixels. The FitViewport maps the
 *  1280x720 world onto 1920x1080 (1.5x), so world offsets must be scaled when
 *  the frame is sampled. PNG lands in build/ for eyeballing. */
class ReproShipBulletsTest {
    private static final int W = 1920, H = 1080;
    private static final float S = 1.5f; // world -> screen scale at 1920x1080

    @Test
    void shipAndBulletsRenderAt1920x1080() throws Exception {
        AtomicReference<int[]> shot = new AtomicReference<>();
        AtomicReference<Throwable> boom = new AtomicReference<>();

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(W, H);
        cfg.setInitialVisible(false);
        cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            Ui ui;
            SpriteBatch batch;
            Flow flow;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    flow = new Flow();
                    flow.set(new HomeScreen(flow, ui, batch));
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                    for (int i = 0; i < 30; i++) flow.render(1 / 60f); // past the fade
                    for (int i = 0; i < 2; i++) flow.render(1 / 60f);

                    World w = worldOf((GameScreen) flow.top());
                    // live rounds: up, down and left of the hull, one frame of travel
                    w.spawnBullet(w.ship.x, w.ship.y + 40, (float) (Math.PI / 2), WeaponDef.SINGLE, 1f, 1f);
                    w.spawnBullet(w.ship.x, w.ship.y - 40, (float) (-Math.PI / 2), WeaponDef.SINGLE, 1f, 1f);
                    w.spawnBullet(w.ship.x - 120, w.ship.y, 0f, WeaponDef.SINGLE, 1f, 1f);
                    flow.render(1 / 60f);
                    shot.set(frame());
                    writePng(shot.get(), "build/repro-frame.png");
                    flow.dispose();
                    ui.dispose();
                    batch.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            World worldOf(GameScreen gs) {
                try {
                    java.lang.reflect.Field f = GameScreen.class.getDeclaredField("world");
                    f.setAccessible(true);
                    return (World) f.get(gs);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            int[] frame() {
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("repro crashed", boom.get());
        int[] px = shot.get();

        // The ship sits at the camera centre (960,540 screen). The rim circles are
        // FILLED discs (r 26 and 36 world = 39 and 54 screen) at alpha 0.25/0.35, so
        // they top out at ~64 brightness; the hull sprite's own ink is >100. Sampling
        // >100 inside the hull's 72px footprint therefore counts the sprite, not the rings.
        int cx = 960, cy = 540;
        int sprite = count(px, cx - 36, cx + 36, cy - 36, cy + 36, 100);
        // rounds: 40 world up/down, 120 world left, plus one frame (~12 world) of flight
        int upY = (int) (cy + (40 + 12) * S), dnY = (int) (cy - (40 + 12) * S);
        int ltX = (int) (cx - (120 - 12) * S);
        int bUp = count(px, cx - 20, cx + 20, upY - 20, upY + 20, 100);
        int bDn = count(px, cx - 20, cx + 20, dnY - 20, dnY + 20, 100);
        int bLt = count(px, ltX - 20, ltX + 20, cy - 20, cy + 20, 100);
        System.out.println("REPRO 1920x1080: hull sprite px=" + sprite
                + " bullets up/down/left=" + bUp + "/" + bDn + "/" + bLt);
        org.junit.jupiter.api.Assertions.assertTrue(sprite > 100,
                "the hull sprite draws at the camera centre (got " + sprite + " bright px)");
        org.junit.jupiter.api.Assertions.assertTrue(bUp > 20 && bDn > 20 && bLt > 20,
                "the rounds draw (up=" + bUp + " down=" + bDn + " left=" + bLt + ")");
    }

    private static int count(int[] px, int x0, int x1, int y0, int y1, int threshold) {
        int n = 0;
        for (int y = Math.max(0, y0); y < Math.min(H, y1); y++)
            for (int x = Math.max(0, x0); x < Math.min(W, x1); x++) {
                int p = px[y * W + x];
                if ((p & 0xff) + ((p >> 8) & 0xff) + ((p >> 16) & 0xff) > threshold) n++;
            }
        return n;
    }

    private static void writePng(int[] px, String path) throws Exception {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int p = px[y * W + x];
                int r = p & 0xff, g = (p >> 8) & 0xff, b = (p >> 16) & 0xff;
                img.setRGB(x, H - 1 - y, (r << 16) | (g << 8) | b);
            }
        File out = new File(path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
    }
}
