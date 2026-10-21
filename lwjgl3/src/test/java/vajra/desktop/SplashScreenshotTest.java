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
import vajra.screen.HomeScreen;
import vajra.screen.SplashScreen;
import vajra.screen.Ui;

/** The studio card, frame by frame on the real Flow: black through the delay, the SAHAH logo
 *  rising on it, a long hold, then the slow outro back to black. Needs a GL context, so it
 *  lives in lwjgl3. PNGs land in build/ for eyeballing.
 *
 *  <p>The field is the menu's own ink, so the last splash frame and the first menu frame are the
 *  same colour and the handover cannot flash. */
class SplashScreenshotTest {
    private static final int W = 1280, H = 720;
    private static final int INK = 13, INK_G = 13, INK_B = 26; // the menu's field

    @Test
    void theLogoWaitsThenRisesHoldsAndFadesOutOnBlack() {
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
            SplashScreen splash;
            float clock;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    flow = new Flow();
                    splash = new SplashScreen(flow, ui, batch);
                    flow.set(splash); // it has to be ON the stack or paint() draws nothing

                    int[] start = at(0f);
                    writePng(start, "build/splash-0-black.png");
                    int[] mid = at(SplashScreen.DELAY + SplashScreen.FADE_IN * 0.5f);
                    writePng(mid, "build/splash-1-rising.png");
                    int[] up = at(SplashScreen.DELAY + SplashScreen.FADE_IN);
                    writePng(up, "build/splash-2-up.png");
                    int[] hold = at(SplashScreen.DELAY + SplashScreen.FADE_IN
                            + SplashScreen.HOLD * 0.5f);
                    writePng(hold, "build/splash-3-hold.png");
                    int[] last = at(SplashScreen.TOTAL - 0.02f);
                    writePng(last, "build/splash-4-out.png");

                    // the menu it hands to, so the two fields can be compared
                    flow.set(new HomeScreen(flow, ui, batch));
                    int[] menu = frame();

                    // black through the delay: the field is up, nothing is on it
                    check(near(start, INK, INK_G, INK_B), "the field is up at t=0", failures);
                    check(logoPixels(start) < 50, "and nothing drawn yet ("
                            + logoPixels(start) + " px)", failures);
                    check(near(mid, INK, INK_G, INK_B), "still black mid-rise", failures);

                    // the logo, up and held
                    int up_px = logoPixels(up), hold_px = logoPixels(hold);
                    check(up_px > 6000, "the logo is up (" + up_px + " px)", failures);
                    check(hold_px > 6000, "and held (" + hold_px + " px)", failures);
                    check(Math.abs(up_px - hold_px) < up_px * 0.25,
                            "at full alpha through the hold", failures);

                    // and back to the menu's own field by the swap, so it cannot flash
                    check(near(last, INK, INK_G, INK_B),
                            "the last frame is the menu's field", failures);
                    int menuField = menu[8 * W + 8];
                    check(distance(last[8 * W + 8], r(menuField), g(menuField), b(menuField)) == 0,
                            "and byte-identical to the menu's", failures);
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            /** Drive the splash to the ABSOLUTE time and grab that frame. The clock is tracked
             *  here because the splash's own `t` is private and each call must continue from
             *  where the last left off - stepping by a delta runs it past the end. */
            int[] at(float time) {
                while (clock + 1 / 120f < time) {
                    splash.update(1 / 120f);
                    clock += 1 / 120f;
                }
                return frame();
            }

            int[] frame() {
                Gdx.gl.glClearColor(0, 0, 0, 1);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                flow.paint();
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }

            /** The field colour, sampled in a corner the logo never reaches. */
            boolean near(int[] px, int cr, int cg, int cb) {
                return distance(px[8 * W + 8], cr, cg, cb) == 0;
            }

            int distance(int p, int cr, int cg, int cb) {
                return Math.abs(r(p) - cr) + Math.abs(g(p) - cg) + Math.abs(b(p) - cb);
            }

            /** Pixels inside the logo's box that are not the field - i.e. the mark itself. */
            int logoPixels(int[] px) {
                int n = 0;
                for (int y = 200; y < 520; y++)
                    for (int x = 480; x < 800; x++) {
                        int p = px[y * W + x];
                        if (Math.abs(r(p) - INK) <= 3 && Math.abs(g(p) - INK_G) <= 3
                                && Math.abs(b(p) - INK_B) <= 3) continue;
                        n++;
                    }
                return n;
            }

            // glReadPixels gives RGBA bytes; the IntBuffer is little-endian, so R is the low
            // byte. Getting this backwards paints the orange logo blue and every colour check
            // silently fails.
            static int r(int p) { return p & 0xff; }
            static int g(int p) { return (p >> 8) & 0xff; }
            static int b(int p) { return (p >> 16) & 0xff; }

            void check(boolean ok, String what, List<String> f) { if (!ok) f.add(what); }

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

        if (boom.get() != null) throw new AssertionError("splash render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "splash checks failed:\n  "
                + String.join("\n  ", failures));
    }
}