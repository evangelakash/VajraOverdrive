package vajra.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.net.Net;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.Score;
import vajra.world.World;

import java.util.Locale;

/** Everything drawn in UI space after the world closes. In flight: the hull / shield / boost
 *  block (HudBars, top-left) with the hull-and-purse line under it, the score and combo
 *  (top-centre), the loka indicator and yojana tape (right wall, centre), the compass
 *  ({@link HudCompass}, the bottom-left corner), and the weapon plate riding directly on the
 *  shard gauge (bottom-right, one stack). Over the run: the victory banner and the results
 *  card. In the hangar: nothing but the hangar.
 *
 *  <p>Each frame runs in three steps, and the order is the point:
 *  <ol>
 *   <li>{@link #refresh}: rebuild every cached string and measurement whose value moved. Nothing
 *       after this step formats or measures anything it does not have to.</li>
 *   <li>shape pass: every line, bar and box (a ShapeRenderer cannot live inside an open batch).</li>
 *   <li>batch pass: art and words.</li>
 *  </ol>
 *  Because refresh comes first, a shape that depends on a measured string never lags a frame
 *  behind it. No key bindings are drawn here — the control screen is the key reference. */
public final class Hud {
    private final Ui ui;
    private final HudBars bars = new HudBars();            // the mask bar block
    private final WeaponCards cards = new WeaponCards();   // the 15 weapon face cards
    private final ShardGauge shards;                       // bottom-right shard + forge readout
    private final HudCompass compass;                      // bottom-left
    private final GlyphLayout measure = new GlyphLayout();

    // --- WEAPON PLATE: the fitted pattern's face card, bottom-right, riding directly on the
    //     shard gauge — its bottom edge always sits one gap above the gauge's top row, so the
    //     block that grows a row per paid element carries the plate up with it. Public: the
    //     screenshot test samples it; cardY(w) is the anchor. ---
    public static final float CARD = 36;
    public static final float CARD_X = 1230;
    private static final float PLATE_GAP = 12;             // gauge top row -> card bottom

    /** The weapon card's bottom edge, directly above the shard gauge. */
    public static float cardY(World w) {
        return ShardGauge.MARGIN + ShardGauge.height(ShardGauge.paidElements(w).length) + PLATE_GAP;
    }

    // --- VITALS LINE: which hull you fly and what is in the purse, one dim line under the
    //     bar block. Public: the screenshot test samples it. ---
    public static final float VITALS_Y = HudBars.LIVES_Y - 20;

    // --- THE CREW: co-op pilots under the vitals, each row their seat colour's swatch and
    //     name, then their round-trip ping in the plain Arial face — green when quick, red
    //     when slow. Your own row reads "-" (a delay to yourself is not a thing). Rows hide
    //     entirely in single-player, the hangar and the results card. ---
    public static final float CREW_X = HudBars.X, CREW_Y = HudBars.LIVES_Y - 46, CREW_PITCH = 17;
    /** The crew plate's width: the name column plus the ping column, with the plate's own
     *  margin on each side. Wide enough for a ten-letter name and a three-digit ping. */
    public static final float CREW_W = 150;
    /** The name column's budget: the name is trimmed to it rather than run into the ping. */
    private static final float CREW_NAME_W = 96;
    /** Where the ping column ends (right-aligned, inside the plate). */
    private static final float CREW_PING_R = CREW_X + CREW_W - 8;

    // --- LOKA INDICATOR and DEPTH TAPE (right wall, centre). The game starts in Bhu Loka: the
    //     tape and the yojana reading only come alive at the grand gate (World.ATALA_Y), where
    //     yojana 0 begins. Scripture (SB 5.24.7, Vishnu Purana): Atala is the first of the seven
    //     subterranean lokas, 10,000 yojanas deep (one yojana ~ 8 miles) — Atala's 19,000 px IS
    //     10,000 yojanas. ---
    static final float YOJANA_PX = 1.9f;
    static final float PIP_GAP = 16, COL_H = 30 * PIP_GAP; // 30 pips in the column
    static final float PIP_YOJANA = 50;                    // one pip every 50 yojanas
    private static final float PIP_W = 12, PIP_H = 1.5f;
    static final float COL_TOP = Ui.UI_H / 2 + COL_H / 2;  // clear of the top-right corner
    static final float IND_MID = Ui.UI_H / 2;              // the text stack centres on mid-screen
    private static final float RIGHT = Ui.UI_W - 14;       // mirrors the left bar margin

    // --- RESULTS CARD: every run — death, the blade whole, Bala fallen — ends on one framed
    //     card. Death and the blade show it at once; a won run plays its 4 s banner lap first,
    //     exactly when GameScreen freezes the world. ---
    private static final float END_X = Template.COL - 320, END_Y = 280, END_W = 640, END_H = 230;
    private static final float END_TITLE_Y = 470, END_SUB_Y = 436;
    private static final float[] END_LINES = { 396, 368, 340 };

    /** Yojana reading: depth below the grand gate, so 0 at the gate and 10,000 at the ceiling.
     *  Bhu Loka (below the gate) clamps to 0 — the tape stays hidden there anyway. */
    static int yojanaAt(float worldY) {
        return (int) Math.round(Math.max(0, worldY - World.ATALA_Y) / YOJANA_PX);
    }

    /** Offset of pip i down the column for a given depth into Atala: climbing increases it
     *  (the tape falls down the screen), dropping decreases it (the tape rises), wrapping
     *  end to end. */
    static float pipY(int i, float depth) {
        float v = i * PIP_GAP + depth * (PIP_GAP / (PIP_YOJANA * YOJANA_PX));
        return v - (float) Math.floor(v / COL_H) * COL_H;
    }

    public Hud(Ui ui) {
        this.ui = ui;
        this.shards = new ShardGauge(ui);
        this.compass = new HudCompass(ui);
    }

    // =================================================================================
    // FRAME
    // =================================================================================

    /** The hangar draws its own OPAQUE backdrop and its own text. The flight HUD is NOT drawn
     *  over it: bars, score, the loka readout, the weapon plate and the vitals line are all noise
     *  on a menu screen — the yojana tape in particular drew straight through the panels.
     *  Game over still gets a translucent dimmer — there the frozen world IS the point. */
    public void render(SpriteBatch batch, World world, ShipRenderer ships, ShopUi shopUi) {
        boolean hangar = shopUi != null;
        boolean over = world.runEnded();
        refresh(world, hangar, over);

        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        shapePass(world, hangar, over, shopUi);
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        batchPass(batch, world, hangar, over, ships, shopUi);
        batch.end();
    }

    private void shapePass(World w, boolean hangar, boolean over, ShopUi shopUi) {
        if (!hangar && w.gameOver) {
            ui.shapes.setColor(0, 0, 0, 0.78f);
            ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H);
        }
        if (over) { // the results card: ink plate + the shared border, both in this pass
            ui.shapes.setColor(UiTheme.INK_R, UiTheme.INK_G, UiTheme.INK_B, 0.92f);
            ui.shapes.rect(END_X, END_Y, END_W, END_H);
            Template.panelBorder(ui, END_X, END_Y, END_W, END_H);
        }
        if (hangar) {
            shopUi.drawBackground(w);
            return;
        }
        shards.update(w);
        bars.shapes(ui.shapes, w);
        crewShapes(w);
        depthTape(w);
        compass.shapes(ui.shapes, w);
        shards.shapes(w);
    }

    private void batchPass(SpriteBatch batch, World w, boolean hangar, boolean over,
                           ShipRenderer ships, ShopUi shopUi) {
        if (!hangar) {
            drawScore(batch);
            drawIndicator(batch, w);
            compass.label(batch, w);
            drawWeapon(batch, w);
            drawVitals(batch);
            drawCrew(batch, w);
            shards.draw(batch, w);
        }
        if (w.victoryT >= 0 && w.victoryT < World.VICTORY_LAP) {
            float a = w.victoryT < 3f ? 1 : World.VICTORY_LAP - w.victoryT;
            Template.title(ui, batch, "ATALA CONQUERED", Template.CARD_Y, a);
            Template.sub(ui, batch, "the pit is yours — Bala has fallen", Template.CARD_SUB_Y, a);
        }
        if (over) drawEndCard(batch, w);
        if (hangar) shopUi.draw(batch, w, ships, cards);
        drawSessionBanner(batch, w);
    }

    /** THE SESSION DIED. A host that closes its window leaves its guests in a run that can
     *  never end: the world freezes and nobody is coming. The banner says so, and says what
     *  to do about it — otherwise the crew block simply vanishes and the pilot is stranded. */
    private void drawSessionBanner(SpriteBatch batch, World w) {
        vajra.net.Session s = Net.session;
        if (s == null || s.state() != vajra.net.Session.State.LOST || w.runEnded()) return;
        Template.title(ui, batch, "THE HOST IS GONE", Template.CARD_Y, 1);
        Template.sub(ui, batch, "press ESC to leave the run", Template.CARD_SUB_Y, 1);
    }

    // =================================================================================
    // CACHE — every string and measurement the draw steps read, rebuilt only when it moves.
    // =================================================================================

    private long shownPts = -1;
    private int shownCombo = -1;
    private String scoreStr = "", comboStr = "";

    private int shownRegion = -1, shownYoj = -1;
    private String lokaStr = "", regStr = "", yojStr = "";

    private int shownWeapon = -1;
    private String weaponStr = "";
    private float weaponNameW;

    private ShipDef shownHull;
    private int shownCoins = -1;
    private String vitalsStr = "";

    private String[] resultLines;
    private long resPts = -1, resBest = -1;
    private int resCoins = -1, resShards = -1;

    private void refresh(World w, boolean hangar, boolean over) {
        refreshResults(w, over);
        if (hangar) return;

        comboT = w.score.comboT; // drains continuously: read every frame, never cached
        if (w.score.points != shownPts || w.score.combo != shownCombo) {
            shownPts = w.score.points;
            shownCombo = w.score.combo;
            scoreStr = w.score.digits();
            comboStr = w.score.combo >= 2 ? w.score.combo + " ×" + w.score.mult() : "";
        }

        int region = w.currentRegionIndex();
        if (region != shownRegion) {
            shownRegion = region;
            lokaStr = w.lokaName().toUpperCase(Locale.ROOT) + " LOKA";
            regStr = w.currentRegion();
        }
        if (region != 0) {
            int yoj = yojanaAt(w.ship.y);
            if (yoj != shownYoj) {
                shownYoj = yoj;
                yojStr = Integer.toString(yoj);
            }
        }

        int wi = w.hangar.weaponIndex;
        if (wi != shownWeapon) {
            shownWeapon = wi;
            weaponStr = WeaponDef.ALL[wi].name.toUpperCase(Locale.ROOT);
            measure.setText(ui.hud, weaponStr);
            weaponNameW = measure.width;
        }

        if (w.ship.def != shownHull || w.hangar.coins != shownCoins) {
            shownHull = w.ship.def;
            shownCoins = w.hangar.coins;
            vitalsStr = w.ship.def.name.toUpperCase(Locale.ROOT) + "   ·   " + w.hangar.coins + "c";
        }

        refreshCrew(w);
    }

    // --- THE CREW: names read off the session roster, pings off the session's measures.
    //     Rebuilt only when the session's epoch moves (roster churn or a fresh measure):
    //     by design the block never touches String.format or measure() in the hot loop. ---

    private long crewEpoch = -1;
    private int[] crewIds = new int[0];
    private String[] crewNames = new String[0];
    /** The plate is dark enough to read over the game's bright rock: the rock is near-white
     *  (0.91), so a 45% scrim left the names at about 1.4:1 — unreadable in the open. */
    private static final Color CREW_BACKDROP = new Color(0, 0, 0, 0.72f);
    private static final int CREW_PING_GREEN = 90; // ms: at/below reads quick, above reads slow

    private void refreshCrew(World w) {
        vajra.net.Session s = Net.session;
        if (s == null || !Net.active() || w.runEnded()
                || (s.epoch() == crewEpoch && crewNames.length == s.count())) return;
        crewEpoch = s.epoch();
        crewIds = new int[s.count()];
        crewNames = new String[s.count()];
        for (int i = 0; i < s.count(); i++) {
            crewIds[i] = s.member(i).id;
            // the name is trimmed to its column HERE, once per roster change — the draw loop
            // must not measure text every frame
            crewNames[i] = fitName(s.member(i).name);
        }
    }

    /** A pilot's name, trimmed to the column it lives in: the name is theirs, but the column
     *  is the column's — a long name is cut, not allowed to run into the ping. */
    private String fitName(String name) {
        if (ui.measure(ui.hud, name) <= CREW_NAME_W) return name;
        String cut = name;
        while (cut.length() > 1 && ui.measure(ui.hud, cut + "..") > CREW_NAME_W)
            cut = cut.substring(0, cut.length() - 1);
        return cut + "..";
    }

    /** The crew block's shapes: a dim plate, then one seat swatch per pilot. */
    private void crewShapes(World w) {
        vajra.net.Session s = Net.session;
        if (s == null || !Net.active() || w.runEnded() || crewIds.length == 0) return;
        ui.shapes.setColor(CREW_BACKDROP);
        ui.shapes.rect(CREW_X - 4, CREW_Y - CREW_PITCH * (crewIds.length - 1) - 12, CREW_W,
                CREW_PITCH * (crewIds.length - 1) + 20);
        for (int i = 0; i < crewIds.length; i++) {
            float y = CREW_Y - i * CREW_PITCH;
            ui.shapes.setColor(SeatColors.of(crewIds[i]));
            ui.shapes.rect(CREW_X, y - 9, 9, 9);
        }
    }

    /** The crew block's words: the name in their seat hue, the ping green when quick, red
     *  when slow, "-" until measured. Your own row reads "-" — a delay to yourself has no
     *  meaning, and the plain Arial face keeps it a readout rather than a flourish. */
    private void drawCrew(SpriteBatch batch, World w) {
        vajra.net.Session s = Net.session;
        if (s == null || !Net.active() || w.runEnded() || crewIds.length == 0) return;
        for (int i = 0; i < crewIds.length; i++) {
            float y = CREW_Y - i * CREW_PITCH;
            ui.hud.setColor(SeatColors.of(crewIds[i]));
            ui.hud.draw(batch, crewNames[i], CREW_X + 16, y);
            int p = s.pingTo(crewIds[i]); // your own row reads '-' (a delay to yourself)
            ui.ping.setColor(p <= CREW_PING_GREEN && p > 0 ? SeatColors.GO
                    : p > 0 ? SeatColors.WAIT : Color.GRAY);
            ui.right(batch, ui.ping, p > 0 ? Integer.toString(p) : "-", CREW_PING_R, y);
        }
        ui.hud.setColor(Color.WHITE);
    }

    /** The results are built once per run, not once per frame (they format several numbers) —
     *  and rebuilt if the score, best or purse moves, since the best is persisted around the
     *  moment the run ends. */
    private void refreshResults(World w, boolean over) {
        if (!over) {
            resultLines = null;
            return;
        }
        if (resultLines != null && w.score.points == resPts && w.hangar.best == resBest
                && w.hangar.coins == resCoins && w.hangar.shardCount() == resShards) return;
        resPts = w.score.points;
        resBest = w.hangar.best;
        resCoins = w.hangar.coins;
        resShards = w.hangar.shardCount();
        resultLines = resultsLines(w);
    }

    /** The run's results, one pure builder: score against the persisted best, the purse,
     *  the clock. Package-private: the headless test reads the run back through it. */
    static String[] resultsLines(World w) {
        long best = w.hangar.best;
        int kills = 0;
        for (int k : w.kills) kills += k;
        int t = (int) w.time();
        return new String[] {
                "SCORE " + w.score.digits() + "   ·   BEST "
                        + String.format(Locale.ROOT, "%010d", best)
                        + (best > 0 && w.score.points >= best ? "   NEW" : ""),
                "KILLS " + kills + "   ·   COINS " + w.hangar.coins
                        + "   ·   SHARDS " + w.hangar.shardCount(),
                "TIME " + t / 60 + ":" + String.format(Locale.ROOT, "%02d", t % 60)
        };
    }

    // =================================================================================
    // FLIGHT ELEMENTS
    // =================================================================================

    /** The tape itself: thirty thin pips down the right margin, fractional scroll (no stepping).
     *  Bhu Loka has no tape — it starts at the grand gate, where the meter reads 0. */
    private void depthTape(World w) {
        if (w.currentRegionIndex() == 0) return;
        float depth = w.ship.y - World.ATALA_Y;
        ui.shapes.setColor(1, 1, 1, 0.35f);
        for (int i = 0; i < 30; i++)
            ui.shapes.rect(RIGHT - PIP_W, COL_TOP - pipY(i, depth), PIP_W, PIP_H);
    }

    /** The indicator mid-screen beside the tape: loka name, region name, then the yojana
     *  reading (Atala only — Bhu Loka shows just where you are). Dim on purpose: it rewards a
     *  glance, it does not demand one. */
    private void drawIndicator(SpriteBatch batch, World w) {
        ui.hud.setColor(1, 1, 1, 0.7f);
        ui.right(batch, ui.hud, lokaStr, RIGHT - 20, IND_MID + 36);
        ui.hud.setColor(1, 1, 1, 0.5f);
        ui.right(batch, ui.hud, regStr, RIGHT - 20, IND_MID + 20);
        if (w.currentRegionIndex() != 0) {
            ui.hud.setColor(1, 1, 1, 0.55f);
            ui.right(batch, ui.hud, yojStr, RIGHT - 20, IND_MID + 4);
            ui.hud.setColor(1, 1, 1, 0.35f);
            ui.right(batch, ui.hud, "yojana", RIGHT - 20, IND_MID - 12);
        }
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** Top-centre: the ten-digit score in gold (Rajdhani SemiBold 30); the chain under it while
     *  it runs, its alpha draining with the combo window — the fade is the urgency cue. */
    private void drawScore(SpriteBatch batch) {
        float goldR = HudBars.GOLD[0], goldG = HudBars.GOLD[1], goldB = HudBars.GOLD[2];
        ui.score.setColor(goldR, goldG, goldB, 1);
        ui.centered(batch, ui.score, scoreStr, Ui.UI_H - 14);
        ui.score.setColor(1, 1, 1, 1);
        if (!comboStr.isEmpty()) {
            float a = 0.35f + 0.65f * Math.max(0f, comboT / Score.WINDOW);
            ui.hud.setColor(goldR, goldG, goldB, a);
            ui.centered(batch, ui.hud, comboStr, Ui.UI_H - 42);
            ui.hud.setColor(1, 1, 1, 1);
        }
    }

    /** The combo window left, read from the world each frame (it drains continuously, so it is
     *  the one number here that cannot be cached). */
    private float comboT;

    /** Bottom-right: the fitted weapon's face card sits directly on the shard gauge, its name
     *  to the left. V/B still cycles the loadout — the card and name changing is the whole
     *  cue, no key text needed. */
    private void drawWeapon(SpriteBatch batch, World w) {
        float y = cardY(w);
        batch.draw(cards.get(shownWeapon), CARD_X, y, CARD, CARD);
        ui.hud.setColor(1, 1, 1, 0.85f);
        ui.hud.draw(batch, weaponStr, CARD_X - 10 - weaponNameW, y + CARD / 2f + 5);
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** Top-left, under the bar block: the fitted hull and the purse on one dim line —
     *  "which hull am I flying" and "what can I spend". */
    private void drawVitals(SpriteBatch batch) {
        ui.hud.setColor(1, 1, 1, 0.7f);
        ui.hud.draw(batch, vitalsStr, HudBars.X, VITALS_Y);
        ui.hud.setColor(1, 1, 1, 1);
    }

    // =================================================================================
    // RESULTS CARD
    // =================================================================================

    private void drawEndCard(SpriteBatch batch, World w) {
        Template.title(ui, batch,
                w.gameOver ? "GAME OVER" : w.vajraDone ? "THE VAJRA IS WHOLE" : "ATALA CONQUERED",
                END_TITLE_Y, 1);
        Template.sub(ui, batch, w.gameOver ? "all lives spent"
                        : w.vajraDone ? "seven bands, one blade — the run is won"
                        : "the pit is yours — Bala has fallen",
                END_SUB_Y, 1);
        for (int i = 0; i < resultLines.length; i++)
            Template.line(ui, batch, resultLines[i], END_LINES[i], Template.A_LINE);
        Template.line(ui, batch, "R or click for a new run", END_LINES[2] - 36, Template.A_HINT);
    }

    public void dispose() {
        cards.dispose();
        shards.dispose();
    }
}