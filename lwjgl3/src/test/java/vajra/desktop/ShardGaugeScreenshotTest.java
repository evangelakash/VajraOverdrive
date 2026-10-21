package vajra.desktop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vajra.ship.Hangar;
import vajra.screen.Hud;
import vajra.screen.Ui;
import vajra.world.Score;
import vajra.world.World;

/** The Astra cells in the armament console, rendered for real: the gem, the count and the charge
 *  track all land inside the console, and the console stays on screen. PNGs land in build/ for
 *  eyeballing. */
class ShardGaugeScreenshotTest {
    private static final int W = 1280, H = 720;
    /** 2 spare segments x 3 rows x 3 cols - the denominator spareCentre() reports against. */
    private static final int SPARE_SAMPLES = 2 * 3 * 4;

    @Test
    void theShardBlockRendersWhereItShould() {
        List<String> failures = new ArrayList<>();
        AtomicReference<Throwable> boom = new AtomicReference<>();

        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setWindowedMode(W, H);
        cfg.setInitialVisible(false);
        cfg.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            Ui ui;
            SpriteBatch batch;
            Hud hud;
            FrameBuffer fbo;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    hud = new Hud(ui);
                    fbo = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);

                    // empty: Atala pays Akasha, so one cell, nothing in it
                    World empty = scene();
                    int[] a = shot(empty);
                    writePng(a, "build/shard-0-empty.png");
                    checkBlock(a, empty, "empty block", failures);

                    // held: three whole segments and 60% of the fourth - the charge track mid-fill
                    World held = scene();
                    for (int i = 0; i < 3; i++) held.hangar.addShard(Hangar.AKASHA, 0);
                    int[] b = shot(held);
                    writePng(b, "build/shard-1-held.png");
                    checkBlock(b, held, "held block", failures);
                    // unforged: the four spare segments are open frames, so their centres are
                    // still the background
                    check(spareCentre(b, held) < SPARE_SAMPLES / 4,
                            "before forging the spare segments are HOLLOW sockets ("
                                    + spareCentre(b, held) + " lit px in their centres)", failures);

                    // forged with a release in hand: the gold hairline goes up around the gem
                    World forged = scene();
                    for (int i = 0; i < Hangar.FORGE_COST; i++)
                        forged.hangar.addShard(Hangar.AKASHA, 0);
                    int[] c = shot(forged);
                    writePng(c, "build/shard-2-forged.png");
                    checkBlock(c, forged, "forged block", failures);
                    // forged: the same segments are filled cells, so the centres are lit
                    check(spareCentre(c, forged) > SPARE_SAMPLES * 3 / 5,
                            "once forged the same segments are SOLID cells ("
                                    + spareCentre(c, forged) + " lit px in their centres)", failures);

                    fbo.dispose();
                    hud.dispose();
                    ui.dispose();
                    batch.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            World scene() {
                World w = new World();
                w.ship.y = 4100; // Maya Vana - past the grand gate
                w.ship.hp = w.ship.maxHp();
                w.lives = 3;
                w.score.points = 123456;
                w.score.combo = 7;
                w.score.comboT = Score.WINDOW;
                return w;
            }

            int[] shot(World w) {
                fbo.begin();
                Gdx.gl.glClearColor(13 / 255f, 13 / 255f, 26 / 255f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                hud.render(batch, w, null, null);
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                fbo.end();
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }

            /** Something must actually be drawn in the console's cell area, and the console must stay
             *  on screen. The slab under the cells is a dark leaf, not the backdrop, so "lit" is
             *  anything brighter than that leaf. */
            void checkBlock(int[] px, World w, String what, List<String> f) {
                float cx0 = Hud.consoleX(w) + Hud.WEAPON_W + Hud.CONSOLE_PAD;
                int y0 = (int) Hud.CONSOLE_Y + 4, y1 = (int) (Hud.CONSOLE_Y + Hud.CONSOLE_H - 12);
                int x0 = (int) cx0, x1 = (int) (cx0 + vajra.screen.ShardGauge.width(1));
                int lit = 0;
                for (int y = y0; y <= y1; y++)
                    for (int x = x0; x <= x1; x++) if (litOverLeaf(px[y * W + x])) lit++;
                if (lit < 60) f.add(what + ": nothing drawn in the cell (" + lit + " px)");
                float right = Hud.consoleX(w) + Hud.consoleW(w);
                if (right > Ui.UI_W) f.add(what + ": the console runs off the screen at x" + (int) right);
            }

            /** The dark leaf under the cells over the test backdrop is about (13,10,11). */
            boolean litOverLeaf(int p) {
                return Math.abs(r(p) - 13) > 12 || Math.abs(g(p) - 10) > 12 || Math.abs(b(p) - 11) > 12;
            }

            /** Lit pixels in the CENTRE of the two spare segments of Atala's one cell. The
             *  hollow-vs-solid distinction lives entirely in that centre: a socket is a 1 px
             *  frame with the leaf inside it, a cell is filled. Counting the whole segment
             *  cannot tell them apart - both have lit edges. */
            int spareCentre(int[] px, World w) {
                // geometry comes from the gauge itself, never recomputed here
                float cellX = vajra.screen.ShardGauge.cellX(
                        Hud.consoleX(w) + Hud.WEAPON_W + Hud.CONSOLE_PAD, 0);
                int segY = (int) vajra.screen.ShardGauge.segY(Hud.CONSOLE_Y + 4, Hud.CONSOLE_H - 20);
                int n = 0;
                // segments 3 and 4 are the unfilled ones in the "held" scene
                for (int seg = 3; seg < 5; seg++) {
                    int cx = (int) (vajra.screen.ShardGauge.trackX(cellX)
                            + seg * (vajra.screen.ShardGauge.SEG_W + vajra.screen.ShardGauge.SEG_GAP)
                            + vajra.screen.ShardGauge.SEG_W / 2);
                    for (int y = segY + 1; y <= segY + (int) vajra.screen.ShardGauge.SEG_H - 2; y++)
                        for (int x = cx - 1; x <= cx + 1; x++) if (litOverLeaf(px[y * W + x])) n++;
                }
                return n;
            }

            static void check(boolean ok, String what, List<String> f) {
                if (!ok) f.add(what);
            }

            static int r(int p) { return p & 0xff; }
            static int g(int p) { return (p >> 8) & 0xff; }
            static int b(int p) { return (p >> 16) & 0xff; }

            void writePng(int[] px, String path) {
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
                } catch (Exception e) { /* a missing PNG is not a failure */ }
            }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("shard gauge render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "shard gauge checks failed:\n  "
                + String.join("\n  ", failures));
    }
}