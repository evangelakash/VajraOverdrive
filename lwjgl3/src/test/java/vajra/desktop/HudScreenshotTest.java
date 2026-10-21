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

    // --- checks: every sampled pixel sits on the public HudBars layout ---------------------
    // readback rows are bottom-up, exactly like UI y-up; sample the bar midlines.

    private static final int HULL_Y = (int) (HudBars.TOP - HudBars.HULL_H / 2);
    private static final int SHIELD_Y = (int) (HudBars.SHIELD_TOP - HudBars.SHIELD_H / 2);
    private static final int BOOST_Y = (int) (HudBars.BOOST_TOP - HudBars.BOOST_H / 2);
    private static final int MID_X = (int) (HudBars.X + HudBars.W / 2);
    private static final int FILL_X = (int) (HudBars.X + 40);        // inside every fraction
    private static final int H_TRACK_X = (int) (HudBars.X + 160);    // past hull 50%
    private static final int S_TRACK_X = (int) (HudBars.X + 180);    // past shield 40%
    private static final int B_TRACK_X = (int) (HudBars.X + 200);    // past boost 70%
    // the score sits top-centre: ten gold digits on the top line, the combo chain under it
    private static final int SCORE_X0 = 500, SCORE_X1 = 780;
    private static final int SCORE_Y0 = 684, SCORE_Y1 = 712; // ui score, baseline UI_H-14
    private static final int COMBO_Y0 = 656, COMBO_Y1 = 684; // ui.hud, baseline UI_H-42

    private static void checkReady(int[] px, World w, List<String> f) {
        // no frame: a full hull reaches the bar's right edge with fill colour, and no gold
        // outline is left anywhere on the block
        expectNear(px, (int) (HudBars.X + HudBars.W) - 2, HULL_Y, 34, 214, 0, 18,
                "hull fill reaches the right edge (frameless)", f);
        int gold = countGold(px, (int) HudBars.X, (int) (HudBars.X + HudBars.W),
                (int) (HudBars.BOOST_TOP - HudBars.BOOST_H), (int) HudBars.TOP);
        check(gold == 0, "outline must be gone (" + gold + " stray gold px)", f);
        // fills: hull green (full), shield cyan (40%), boost orange (70%)
        expectNear(px, MID_X, HULL_Y, 34, 214, 0, 18, "hull fill green", f);
        expectNear(px, FILL_X, SHIELD_Y, 0, 226, 235, 25, "shield fill cyan at 40%", f);
        expectNear(px, FILL_X, BOOST_Y, 243, 74, 10, 25, "boost fill orange at 70%", f);
        // unfilled interiors are the dark track
        expectNear(px, S_TRACK_X, SHIELD_Y, 6, 6, 12, 12, "shield track past 40%", f);
        expectNear(px, B_TRACK_X, BOOST_Y, 6, 6, 12, 12, "boost track past 70%", f);
        // nothing else: the top-right corner and the screen centre stay clear
        int stray = countClear(px, 1180, 1270, 620, 715);
        check(stray == 0, "top-right corner must stay clear (" + stray + " stray px)", f);
        int centre = countClear(px, 560, 720, 320, 400);
        check(centre == 0, "screen centre must stay clear (" + centre + " stray px)", f);
        // golden life pips under the bars, and a full gold dash pip (ready)
        int pips = countGold(px, (int) HudBars.X,
                (int) (HudBars.X + 2 * HudBars.LIVES_PITCH + 2 * HudBars.LIVES_R),
                (int) (HudBars.LIVES_Y - HudBars.LIVES_R),
                (int) (HudBars.LIVES_Y + HudBars.LIVES_R + 1));
        check(pips > 40, "three golden life pips below the bars (" + pips + " px)", f);
        expectNear(px, (int) (HudBars.DASH_X + HudBars.DASH_W / 2), 695,
                255, 215, 0, 25, "dash pip gold when ready", f);
        expectNear(px, (int) (HudBars.DASH_X + HudBars.DASH_W / 2), (int) (HudBars.TOP - 2),
                255, 215, 0, 25, "dash pip full to the top", f);
        // score + combo, top-centre in gold: ten digits on the top line, 7 x2 under it
        int score = countGold(px, SCORE_X0, SCORE_X1, SCORE_Y0, SCORE_Y1);
        check(score > 100, "gold score digits top-centre (" + score + " px)", f);
        int combo = countGold(px, SCORE_X0, SCORE_X1, COMBO_Y0, COMBO_Y1);
        check(combo > 20, "gold combo line under the score (" + combo + " px)", f);
        // the yojana tape: thin white pips down the right wall's centre (countClear counts
        // drawn pixels, so presence checks compare it directly against the box)
        int tape = countClear(px, 1254, 1266, 264, 457);
        check(tape > 30, "yojana pips down the right wall (" + tape + " px)", f);
        // the indicator mid-screen beside the tape: ATALA LOKA (y 385-395), Maya Vana
        // (369-379), the reading (353-363), its label (337-347) — right-aligned at x 1246
        int loka = countClear(px, 1150, 1250, 382, 398);
        check(loka > 15, "loka name beside the tape (" + loka + " px)", f);
        int region = countClear(px, 1150, 1250, 366, 382);
        check(region > 15, "region name under it (" + region + " px)", f);
        int reading = countClear(px, 1200, 1250, 350, 366);
        check(reading > 15, "the yojana reading under the region (" + reading + " px)", f);
        // the fitted weapon's face card riding directly on the shard gauge — Single Bolt's
        // card, its name to the left, the rects derived off Hud.cardY so they cannot drift.
        // The card art is near-black on the ink field, so this samples LIT pixels
        float cardY = Hud.cardY(w);
        int face = countLit(px, (int) Hud.CARD_X, (int) (Hud.CARD_X + Hud.CARD),
                (int) cardY, (int) (cardY + Hud.CARD));
        check(face > 10, "weapon face card above the shard gauge (" + face + " px)", f);
        int weaponName = countClear(px, (int) Hud.CARD_X - 150, (int) Hud.CARD_X - 10,
                (int) (cardY + Hud.CARD / 2f - 14), (int) (cardY + Hud.CARD / 2f + 4));
        check(weaponName > 20, "weapon name beside the card (" + weaponName + " px)", f);
        // the vitals line under the bar block: hull name + purse (buffer rows are UI y-up)
        int vitals = countClear(px, (int) HudBars.X, (int) HudBars.X + 240,
                (int) (Hud.VITALS_Y - 12), (int) (Hud.VITALS_Y + 14));
        check(vitals > 30, "hull + purse line under the bars (" + vitals + " px)", f);
    }

    private static void checkBhu(int[] px, List<String> f) {
        // below the grand gate the meter is off: no pips, no reading, but loka + region remain
        int tape = countClear(px, 1254, 1266, 264, 457);
        check(tape == 0, "no tape in Bhu Loka (" + tape + " stray px)", f);
        int reading = countClear(px, 1200, 1250, 350, 366);
        check(reading == 0, "no yojana reading in Bhu Loka (" + reading + " stray px)", f);
        int loka = countClear(px, 1150, 1250, 382, 398);
        check(loka > 15, "loka name still shown in Bhu Loka (" + loka + " px)", f);
    }

    private static void checkLow(int[] px, List<String> f) {
        // hull at 50%: green stops mid-bar, track beyond; shield empty: track everywhere
        expectNear(px, FILL_X, HULL_Y, 34, 214, 0, 18, "hull green at 50%", f);
        expectNear(px, H_TRACK_X, HULL_Y, 6, 6, 12, 12, "hull track past 50%", f);
        expectNear(px, FILL_X, SHIELD_Y, 6, 6, 12, 12, "shield track when empty", f);
        // dash on cooldown: the pip drains, only the track remains
        expectNear(px, (int) (HudBars.DASH_X + HudBars.DASH_W / 2),
                (int) (HudBars.DASH_BOT + HudBars.DASH_H - 3),
                6, 6, 12, 12, "dash pip empty while recharging", f);
        // score survives the chain breaking; the combo line is gone with it
        int score = countGold(px, SCORE_X0, SCORE_X1, SCORE_Y0, SCORE_Y1);
        check(score > 100, "gold score digits still there (" + score + " px)", f);
        int combo = countGold(px, SCORE_X0, SCORE_X1, COMBO_Y0, COMBO_Y1);
        check(combo == 0, "combo line clears when the chain breaks (" + combo + " px)", f);
    }

    /** The docked hangar: the divider between the columns, the hull preview, a face card on
     *  every weapon row and the highlighted pattern's stats line. The dim is ink-on-ink over
     *  the test backdrop, so countClear still finds everything drawn on top of it. */
    /** The hangar is now opaque and tabbed (ShopUi). These sample the real panel rects, so if
     *  the layout moves they follow it. Each tab is checked on its own scene. */
    private static void checkShop(int[] px, List<String> f) {
        // opaque backdrop: the corner must be the hangar's ink, not the world showing through
        expectNear(px, 6, 714, 13, 13, 26, 16, "opaque hangar backdrop", f);
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
