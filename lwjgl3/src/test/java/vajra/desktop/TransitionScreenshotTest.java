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
import vajra.world.World;

/** The title-to-run transition, end to end on the real Flow: the home frame must show the
 *  logo and the gold PLAY row, PLAY must hand straight over to the run (the prologue cutscene
 *  first, ESC skippable), and the run's first stable frame must show the ship and the HUD (the
 *  FitViewport-never-applied regression: lights left glViewport at 0x0 and every frame after
 *  the first drew nothing). Also catches the warden card on the seal lift. Needs a GL context,
 *  so it lives in lwjgl3; PNGs land in build/ for eyeballing. */
class TransitionScreenshotTest {
    private static final int W = 1280, H = 720;

    @Test
    void playHandsOverToARunThatDrawsShipAndHud() {
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

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    flow = new Flow();
                    flow.set(new HomeScreen(flow, ui, batch)); // exactly VajraGame's boot

                    // 1. the idle title: the bronze name under the ruler and the lit PLAY leaf
                    int[] home = frame();
                    writePng(home, "build/trans-home.png");
                    check(gold(home, 330, 950, 600, 650) > 50,
                            "home frame shows the gold name (" + gold(home, 330, 950, 600, 650) + " px)",
                            failures);
                    // PLAY is the selected row: the pale leaf in hand sits at UI 482-518
                    check(gold(home, 560, 720, 484, 516) > 30,
                            "home frame shows the lit PLAY leaf (" + gold(home, 560, 720, 484, 516) + " px)",
                            failures);

                    // 1b. the PROFILE row opens the pilot's page; ESC hands back home
                    flow.keyDown(com.badlogic.gdx.Input.Keys.DOWN); // PLAY -> MULTIPLAYER
                    flow.keyDown(com.badlogic.gdx.Input.Keys.DOWN); // MULTIPLAYER -> CONTROLS
                    flow.keyDown(com.badlogic.gdx.Input.Keys.DOWN); // CONTROLS -> PROFILE
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                    check(flow.top() instanceof vajra.screen.ProfileScreen,
                            "PROFILE opens the pilot page", failures);
                    int[] profile = frame();
                    writePng(profile, "build/trans-profile.png");
                    // the pilot's name, one gold letter per cell at UI 462-496 (bottom-up readback)
                    check(gold(profile, 400, 880, 458, 500) > 30,
                            "pilot page shows the name line (" + gold(profile, 400, 880, 458, 500) + " px)",
                            failures);
                    // the ledger lines under it (whitish menu ink): BEST/RUNS/KILLS/POINTS/TIME
                    check(bright(profile, 400, 880, 290, 410) > 50,
                            "pilot page shows the ledger lines (" + bright(profile, 400, 880, 290, 410) + " px)",
                            failures);
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                    check(flow.top() instanceof HomeScreen, "ESC leaves the pilot page", failures);
                    flow.keyDown(com.badlogic.gdx.Input.Keys.UP); // the cursor stayed on PROFILE:
                    flow.keyDown(com.badlogic.gdx.Input.Keys.UP); // walk it back to PLAY for the handover
                    flow.keyDown(com.badlogic.gdx.Input.Keys.UP);

                    // 2. PLAY starts the run at once
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ENTER);
                    check(flow.top() instanceof GameScreen, "PLAY hands over to the run", failures);
                    for (int i = 0; i < 30; i++) frame(); // 0.5 s > the 0.3 s fade

                    // 3. past the white fade: ship centre-screen and the HUD bars drawn
                    for (int i = 0; i < 5; i++) frame(); // the run picks straight back up
                    int[] game = frame();
                    writePng(game, "build/trans-game.png");
                    check(shipsBright(game, 616, 664, 336, 384) > 150,
                            "run frame shows the ship at the camera centre (" + shipsBright(game, 616, 664, 336, 384) + " px)",
                            failures);
                    check(hullMeter(game) > 150,
                            "HUD hull meter still draws after the lights pass (" + hullMeter(game) + " px)",
                            failures);

                    // 4. ESC freezes the run under a pause card; ESC again pops straight back
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                    int[] pause = frame();
                    writePng(pause, "build/trans-pause.png");
                    check(flow.top() instanceof vajra.screen.PauseScreen,
                            "ESC pushes the pause overlay", failures);
                    // PAUSED title (read back off the PNG it just wrote — the ESC frame's
                    // back buffer is unreliable on some drivers): title glyphs land roughly
                    // top-down 145-190, so bottom-up 530-575 after PNG readback.
                    int[] pauseTitle = readPng("build/trans-pause.png");
                    // the menus no longer follow the loka's accent: the pause title is the bronze every
                    // menu heading wears (the bazaar's one heading colour)
                    check(gold(pauseTitle, 500, 800, 530, 575) > 50,
                            "PAUSED title in bronze (" + gold(pauseTitle, 500, 800, 530, 575) + " px)",
                            failures);
                    check(bright(pause, 400, 880, 205, 275) > 100,
                            "run-state lines under the title (" + bright(pause, 400, 880, 205, 275) + " px)",
                            failures);
                    flow.keyDown(com.badlogic.gdx.Input.Keys.ESCAPE);
                    check(flow.top() instanceof GameScreen, "ESC resumes the run", failures);

                    // 5. the warden beat: fill the quota and Bala spawns
                    World w = ((GameScreen) flow.top()).world();
                    for (int i = 0; i < w.quota.length; i++) w.kills[i] = w.quota[i];
                    for (int i = 0; i < 3; i++) frame();
                    check(w.boss != null, "the seal lift spawns Bala", failures);
                    writePng(frame(), "build/boss-spawn.png"); // eyeball: the boss arena

                    flow.dispose();
                    ui.dispose();
                    batch.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            /** One frame off the window back buffer — exactly what the game draws to — read
             *  back bottom-up (UI y-up). No wrapping FrameBuffer: Lights' own fbo.end() drops
             *  the binding to the window mid-frame, so a wrapper only ever reads half a frame. */
            int[] frame() {
                flow.render(1 / 60f);
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("transition render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "transition checks failed:\n  "
                + String.join("\n  ", failures));
    }

    // --- checks -----------------------------------------------------------------

    private static int r(int p) { return p & 0xff; }
    private static int g(int p) { return (p >> 8) & 0xff; }
    private static int b(int p) { return (p >> 16) & 0xff; }

    /** Bright (hull, canopy, lit pixels) in a box — stars contribute a handful at most. */
    private static int shipsBright(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (r(px[y * W + x]) + g(px[y * W + x]) + b(px[y * W + x]) > 240) n++;
        return n;
    }

    /** The hull meter's fill in the vitals slab: verdigris (94,178,153) while the hull is sound,
     *  on the hull row's centre line. */
    private static int hullMeter(int[] px) {
        int n = 0, y0 = (int) vajra.screen.HudBars.rowY(0) - 4, y1 = y0 + 8;
        for (int y = y0; y < y1; y++)
            for (int x = (int) vajra.screen.HudBars.BAR_X; x < (int) (vajra.screen.HudBars.BAR_X + vajra.screen.HudBars.BAR_W); x++) {
                int p = px[y * W + x];
                if (Math.abs(r(p) - 94) < 40 && Math.abs(g(p) - 178) < 40 && Math.abs(b(p) - 153) < 40) n++;
            }
        return n;
    }

    /** Gold-ish pixels in a box (the title's Rozha gold counts). */
    private static int gold(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int p = px[y * W + x];
                if (r(p) > 140 && g(p) > 110 && b(p) < 130) n++;
            }
        return n;
    }

    /** The Akasha lavender a run's accent wears on Atala: blue-dominant, pale. */
    private static int lav(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int p = px[y * W + x];
                if (b(p) > 220 && r(p) > 140 && g(p) > 120 && b(p) - r(p) > 40) n++;
            }
        return n;
    }

    /** Bright (near-white text) in a box. */
    private static int bright(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (r(px[y * W + x]) + g(px[y * W + x]) + b(px[y * W + x]) > 400) n++;
        return n;
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) f.add(what);
    }

    /** Read a PNG back as a bottom-up buffer (same layout frame() returns), so a check can
     *  use the exact pixels writePng persisted. */
    private static int[] readPng(String path) {
        try {
            BufferedImage img = ImageIO.read(new File(path));
            int[] px = new int[W * H];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int c = img.getRGB(x, H - 1 - y); // flip back to bottom-up
                    px[y * W + x] = ((c >> 16) & 0xff) | (((c >> 8) & 0xff) << 8)
                            | ((c & 0xff) << 16);
                }
            return px;
        } catch (Exception e) {
            throw new RuntimeException("screenshot read failed", e);
        }
    }

    /** PNG rows are top-down; the GL readback is bottom-up — flip while copying. */
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
