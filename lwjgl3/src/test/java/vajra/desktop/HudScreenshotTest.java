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
import vajra.screen.Hud;
import vajra.screen.HudBars;
import vajra.screen.FxImages;
import vajra.screen.ShipRenderer;
import vajra.screen.ShopUi;
import vajra.screen.Ui;
import vajra.world.Score;
import vajra.world.World;

/** Screenshot autotest for the frameless bar HUD: renders the top-left hull/shield/boost block and
 *  the top-centre score + combo into an off-screen 1280x720 frame buffer, writes PNGs into build/,
 *  and samples pixels against the public HudBars layout — if the mask moves, a fill mis-colors, a
 *  fraction lands wrong or the score drifts, the sampled check fails and says which. Needs a real
 *  GL context (a hidden window), so it lives in lwjgl3, not headless core. */
class HudScreenshotTest {
    private static final int W = 1280, H = 720;
    private static final int[] CLEAR = {13, 13, 26}; // the test scene's backdrop

    @Test
    void maskBarsRenderAndEveryFillLands() {
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
            ShipRenderer ships;
            FrameBuffer fbo;

            @Override
            public void create() {
                try {
                    ui = new Ui();
                    batch = new SpriteBatch();
                    hud = new Hud(ui);
                    ships = new ShipRenderer(new FxImages()); // the hangar's hull preview borrows it
                    fbo = new FrameBuffer(Pixmap.Format.RGBA8888, W, H, false);

                    World w = scene();
                    int[] a = shot(w);
                    writePng(a, "build/hud-scene-ready.png");
                    checkReady(a, w, failures);

                    w.score.breakCombo(); // chain gone: the combo line must clear
                    w.ship.hp = w.ship.maxHp() * 0.5f; // hull half gone
                    w.ship.shieldEnergy = 0;           // shield flat
                    w.ship.dashCd = w.ship.dashCd();   // dash fully on cooldown
                    int[] b = shot(w);
                    writePng(b, "build/hud-scene-low.png");
                    checkLow(b, failures);

                    w.ship.y = 800; // Bhu Loka: no tape, no reading — loka + region still shown
                    int[] c = shot(w);
                    writePng(c, "build/hud-scene-bhu.png");
                    checkBhu(c, failures);

                    w.ship.y = 4100; // back into Atala, then dock: the hangar over the run
                    int[] d = shotShop(w, 0);
                    writePng(d, "build/hud-scene-shop.png");
                    checkShop(d, failures);
                    checkSectionTab(d, "BAY", failures);

                    int[] p = shotShop(w, 1);
                    writePng(p, "build/hud-scene-shop-pattern.png");
                    checkSectionTab(p, "FLEET", failures);

                    int[] t = shotShop(w, 2);
                    writePng(t, "build/hud-scene-shop-tatva.png");
                    checkSectionTab(t, "REFIT", failures);

                    ui.dispose();
                    batch.dispose();
                    hud.dispose();
                    ships.dispose();
                    fbo.dispose();
                } catch (Throwable t) {
                    boom.set(t);
                } finally {
                    Gdx.app.exit();
                }
            }

            /** Clear the bound frame buffer, draw the HUD, read the pixels back. */
            int[] shot(World w) {
                fbo.begin();
                Gdx.gl.glClearColor(CLEAR[0] / 255f, CLEAR[1] / 255f, CLEAR[2] / 255f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                hud.render(batch, w, ships, null);
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                fbo.end();
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }

            /** The hangar docked, on one of its three tabs. */
            int[] shotShop(World w, int tab) {
                fbo.begin();
                Gdx.gl.glClearColor(CLEAR[0] / 255f, CLEAR[1] / 255f, CLEAR[2] / 255f, 1f);
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                ShopUi ui2 = new ShopUi(ui);
                ui2.setTab(tab);
                hud.render(batch, w, ships, ui2);
                IntBuffer buf = BufferUtils.newIntBuffer(W * H);
                Gdx.gl.glReadPixels(0, 0, W, H, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, buf);
                fbo.end();
                int[] px = new int[W * H];
                buf.get(px);
                return px;
            }
        }, cfg);

        if (boom.get() != null) throw new AssertionError("HUD render crashed", boom.get());
        assertTrue(failures.isEmpty(), () -> "HUD screenshot checks failed:\n  "
                + String.join("\n  ", failures));
    }

    /** The scene: full hull, shield at 40%, boost at 70%, dash ready, three lives, a running
     *  score with a live combo chain (7 kills = x2) under it, and the ship inside Atala (the
     *  indicator and its tape only exist past the grand gate). */
    private static World scene() {
        World w = new World();
        w.ship.y = 4100; // Maya Vana — Atala region 2
        w.ship.hp = w.ship.maxHp();
        w.ship.shieldEnergy = w.ship.shieldCap() * 0.4f;
        w.ship.boostEnergy = 70;
        w.ship.dashCd = 0;
        w.lives = 3;
        w.score.points = 123456;
        w.score.combo = 7;
        w.score.comboT = Score.WINDOW;
        return w;
    }

    // --- checks: every sampled pixel sits on the public layout (HudBars, Hud) --------------
    // readback rows are bottom-up, exactly like UI y-up. Meter fills are sampled at fractions
    // that fall BETWEEN the tenth-ticks (0.05, 0.15 ...), where the fill or the track is clean.

    private static int meterX(float frac) { return (int) (HudBars.BAR_X + HudBars.BAR_W * frac); }

    private static final float[] VERDIGRIS = { 0.370f, 0.700f, 0.600f };
    private static final float[] AMBER = { 0.930f, 0.700f, 0.300f };
    private static final float[] ASH = { 0.600f, 0.680f, 0.740f };
    private static final float[] OCHRE = { 0.880f, 0.620f, 0.270f };
    private static final float[] BRONZE = { 0.860f, 0.700f, 0.460f };

    private static void fill(int[] px, int x, int y, float[] c, String what, List<String> f) {
        expectNear(px, x, y, Math.round(c[0] * 255), Math.round(c[1] * 255), Math.round(c[2] * 255), 28, what, f);
    }

    /** The unfilled part of a meter is the dark track over the test backdrop: near-black. */
    private static void track(int[] px, int x, int y, String what, List<String> f) {
        expectNear(px, x, y, 12, 10, 12, 16, what, f);
    }

    private static void checkReady(int[] px, World w, List<String> f) {
        int hull = (int) HudBars.rowY(0), shield = (int) HudBars.rowY(1), energy = (int) HudBars.rowY(2),
                dash = (int) HudBars.rowY(3);
        // full hull reads verdigris; shield at 40% is ash up to the fraction and track beyond;
        // energy at 70% is ochre up to it; the dash is ready (bronze, full)
        fill(px, meterX(0.55f), hull, VERDIGRIS, "hull fill verdigris when sound", f);
        fill(px, meterX(0.15f), shield, ASH, "shield fill ash at 40%", f);
        track(px, meterX(0.75f), shield, "shield track past 40%", f);
        fill(px, meterX(0.15f), energy, OCHRE, "energy fill ochre at 70%", f);
        track(px, meterX(0.85f), energy, "energy track past 70%", f);
        fill(px, meterX(0.55f), dash, BRONZE, "dash bronze when ready", f);
        // three lives, as wheels on the lives row
        int wheels = countLit(px, (int) HudBars.X + 10, (int) HudBars.X + 80,
                (int) HudBars.LIVES_Y - 9, (int) HudBars.LIVES_Y + 9);
        check(wheels > 60, "three life wheels under the meters (" + wheels + " px)", f);
        // the hull flown and the purse, right-aligned on the lives row
        int purse = countLit(px, (int) HudBars.X + 150, (int) (HudBars.X + HudBars.W - 10),
                (int) HudBars.LIVES_Y - 8, (int) HudBars.LIVES_Y + 8);
        check(purse > 30, "hull name and purse on the lives row (" + purse + " px)", f);
        // the figures, right-aligned beside each meter
        int figures = countLit(px, (int) (HudBars.X + HudBars.W - 60), (int) (HudBars.X + HudBars.W - 10),
                (int) HudBars.rowY(2) - 7, (int) HudBars.rowY(0) + 7);
        check(figures > 60, "the hull, shield and energy figures (" + figures + " px)", f);
        // nothing at the screen's centre: the middle of the screen is the fight
        int centre = countClear(px, 560, 720, 320, 400);
        check(centre == 0, "screen centre must stay clear (" + centre + " stray px)", f);

        // the score slab, top-centre: ten digits, and the chain under it with its meter
        float top = Ui.UI_H - 14;
        int score = countLit(px, 500, 780, (int) (top - 40), (int) (top - 10));
        check(score > 100, "bone score digits top-centre (" + score + " px)", f);
        int chain = countLit(px, 540, 740, (int) (top - 62), (int) (top - 44));
        check(chain > 20, "the chain line under the score (" + chain + " px)", f);

        // the location tag and the route, right edge
        int loka = countLit(px, (int) Hud.LOC_X + 8, (int) (Hud.LOC_X + Hud.LOC_W - 8),
                (int) (Hud.LOC_TOP - 48), (int) (Hud.LOC_TOP - 10));
        check(loka > 40, "the loka and region names in the tag (" + loka + " px)", f);
        int reading = countLit(px, (int) (Hud.LOC_X + 90), (int) (Hud.LOC_X + Hud.LOC_W - 8),
                (int) (Hud.LOC_TOP - 80), (int) (Hud.LOC_TOP - 56));
        check(reading > 40, "the yojana reading in the tag (" + reading + " px)", f);
        int ruler = countLit(px, (int) Hud.ROUTE_X - 3, (int) Hud.ROUTE_X + 3,
                (int) Hud.ROUTE_BOT, (int) Hud.ROUTE_TOP);
        check(ruler > 300, "the route ruler's line (" + ruler + " px)", f);

        // the armament console: the fitted weapon's name and its figure
        float cx0 = Hud.consoleX(w);
        int weapon = countLit(px, (int) (cx0 + 70), (int) (cx0 + Hud.WEAPON_W - 10),
                (int) (Hud.CONSOLE_Y + 22), (int) (Hud.CONSOLE_Y + Hud.CONSOLE_H - 12));
        check(weapon > 60, "the weapon name and its DPS in the console (" + weapon + " px)", f);
        int face = countLit(px, (int) cx0 + 14, (int) cx0 + 14 + (int) Hud.CARD + 6,
                (int) Hud.CONSOLE_Y + 8, (int) (Hud.CONSOLE_Y + Hud.CONSOLE_H - 8));
        check(face > 10, "the weapon's face in its plate (" + face + " px)", f);
        // the radar dial, bottom-left: its ring (copper) crosses the row through its centre
        int dial = countLit(px, 20, 150, 78, 90);
        check(dial > 20, "the radar dial's ring and ticks (" + dial + " px)", f);
    }

    private static void checkBhu(int[] px, List<String> f) {
        // below the grand gate there is no reading: the tag shows only the loka and region
        int reading = countLit(px, (int) (Hud.LOC_X + 90), (int) (Hud.LOC_X + Hud.LOC_W - 8),
                (int) (Hud.LOC_TOP - 80), (int) (Hud.LOC_TOP - 56));
        check(reading == 0, "no yojana reading in Bhu Loka (" + reading + " stray px)", f);
        int loka = countLit(px, (int) Hud.LOC_X + 8, (int) (Hud.LOC_X + Hud.LOC_W - 8),
                (int) (Hud.LOC_TOP - 48), (int) (Hud.LOC_TOP - 10));
        check(loka > 40, "loka name still shown in Bhu Loka (" + loka + " px)", f);
    }

    private static void checkLow(int[] px, List<String> f) {
        int hull = (int) HudBars.rowY(0), shield = (int) HudBars.rowY(1), dash = (int) HudBars.rowY(3);
        // hull at 50% is worn (amber) to the fraction and track beyond; shield empty: track throughout
        fill(px, meterX(0.25f), hull, AMBER, "hull amber at 50%", f);
        track(px, meterX(0.75f), hull, "hull track past 50%", f);
        track(px, meterX(0.25f), shield, "shield track when empty", f);
        // dash on cooldown: the meter is empty
        track(px, meterX(0.25f), dash, "dash meter empty while recharging", f);
        // score survives the chain breaking; the chain line goes with it
        float top = Ui.UI_H - 14;
        int score = countLit(px, 500, 780, (int) (top - 40), (int) (top - 10));
        check(score > 100, "bone score digits still there (" + score + " px)", f);
        int chain = countLit(px, 540, 740, (int) (top - 62), (int) (top - 44));
        check(chain == 0, "the chain line clears when the chain breaks (" + chain + " px)", f);
    }

    /** The docked hangar: the divider between the columns, the hull preview, a face card on
     *  every weapon row and the highlighted pattern's stats line. The dim is ink-on-ink over
     *  the test backdrop, so countClear still finds everything drawn on top of it. */
    /** The hangar is now opaque and tabbed (ShopUi). These sample the real panel rects, so if
     *  the layout moves they follow it. Each tab is checked on its own scene. */
    private static void checkShop(int[] px, List<String> f) {
        // opaque backdrop: the left margin, mid-height, must be the dark earth (about 24,17,13
        // there), not the world showing through.
        expectNear(px, 6, 400, 24, 17, 13, 14, "opaque hangar backdrop", f);
        for (int i = 0; i < ShopUi.TAB_NAMES.length; i++) {
            float y = ShopUi.railY(i);
            check(countLit(px, (int) ShopUi.RAIL_X, (int) ShopUi.DIV_X,
                    (int) y - 5, (int) y + 5) > 60,
                    "tab " + ShopUi.TAB_NAMES[i] + " is drawn", f);
        }
        // the purse's right rail now: coins at the corner, then shards, derived from the real
        // rects — anything hardcoded died the last time the wallet moved.
        check(countGold(px, (int) (ShopUi.WALLET_X1 - ShopUi.WALLET_COINS_W),
                (int) ShopUi.WALLET_X1,
                (int) ShopUi.WALLET_Y, (int) (ShopUi.WALLET_Y + ShopUi.WALLET_H)) > 40,
                "coins chip", f);
        int shards = countLit(px,
                (int) (ShopUi.WALLET_SELL_X + ShopUi.WALLET_SELL_W + ShopUi.WALLET_GAP),
                (int) (ShopUi.WALLET_SELL_X + ShopUi.WALLET_SELL_W + ShopUi.WALLET_GAP
                        + ShopUi.WALLET_SHARDS_W),
                (int) ShopUi.WALLET_Y, (int) (ShopUi.WALLET_Y + ShopUi.WALLET_H));
        check(shards > 40, "shards chip (" + shards + " px)", f);
        // the wallet, sampled on its REAL rect. These were hardcoded numbers and the layout
        // moved right underneath them, so the check went on passing a region that is now empty.
        // countLit, not countGold: the scene carries 0 shards, so SELL ALL is correctly drawn
        // DISABLED and its text is dim. The chips are present either way — the badge tick and
        // the label are what this checks, not their brightness.
        int sell = countLit(px, (int) ShopUi.WALLET_SELL_X,
                (int) (ShopUi.WALLET_SELL_X + ShopUi.WALLET_SELL_W),
                (int) ShopUi.WALLET_Y, (int) (ShopUi.WALLET_Y + ShopUi.WALLET_H));
        check(sell > 40, "sell-all chip (" + sell + " px)", f);
    }

    /** Every section tab fills its body with drawn ink — cards (BAY, FLEET) or rows and slugs
     *  (REFIT). Both are full-bright against the ink backdrop, so a lit-pixel count over the
     *  body's interior, derived off the live BODY_ constants, fails if a tab draws nothing. */
    private static void checkSectionTab(int[] px, String section, List<String> f) {
        int body = countLit(px, (int) ShopUi.BODY_X + 8, (int) ShopUi.M1 - 8,
                (int) ShopUi.BODY_Y + 8, (int) ShopUi.BODY_TOP - 6);
        check(body > 800, section + " tab draws its body (" + body + " px)", f);
        // every tab names the run state on the answer line under the body
        int answer = countClear(px,
                (int) (ShopUi.BODY_CX - 120), (int) (ShopUi.BODY_CX + 120),
                (int) (ShopUi.ANSWER_Y - 8), (int) (ShopUi.ANSWER_Y + 8));
        check(answer > 40, section + " answer line (" + answer + " px)", f);
    }

    /** Pixels brighter than the panel ink. The hangar's backdrop and panels are (13,13,26), so
     *  anything above r=40 is text or a wash. countClear cannot see this: it compares against
     *  the test's clear colour, which the opaque hangar covers everywhere. */
    private static int countLit(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (r(rgb(px, x, y)) > 40) n++;
        return n;
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) f.add(what);
    }

    // --- pixel helpers: readback rows are bottom-up, exactly like UI y-up ------------------

    private static int rgb(int[] px, int x, int y) {
        return px[y * W + x];
    }

    private static int r(int p) { return p & 0xff; }

    private static int g(int p) { return (p >> 8) & 0xff; }

    private static int b(int p) { return (p >> 16) & 0xff; }

    private static boolean near(int p, int cr, int cg, int cb, int tol) {
        return Math.abs(r(p) - cr) <= tol && Math.abs(g(p) - cg) <= tol && Math.abs(b(p) - cb) <= tol;
    }

    private static void expectNear(int[] px, int x, int y, int cr, int cg, int cb,
                                   int tol, String what, List<String> f) {
        int p = rgb(px, x, y);
        if (!near(p, cr, cg, cb, tol))
            f.add(what + ": expected (" + cr + "," + cg + "," + cb + ")±" + tol
                    + " at " + x + "," + y + " but got (" + r(p) + "," + g(p) + "," + b(p) + ")");
    }

    private static int countGold(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int p = rgb(px, x, y);
                if (r(p) > 140 && g(p) > 110 && b(p) < 130) n++;
            }
        return n;
    }

    private static int countClear(int[] px, int x0, int x1, int y0, int y1) {
        int n = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++)
                if (!near(rgb(px, x, y), CLEAR[0], CLEAR[1], CLEAR[2], 2)) n++;
        return n;
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
