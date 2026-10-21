package vajra.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.net.Net;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.Score;
import vajra.world.World;

import java.util.Locale;

/** Everything drawn in UI space after the world closes, laid out as four dark bark slabs and two
 *  instruments around the edge of the play, so the middle of the screen is the fight and nothing
 *  else.
 *
 *  <pre>
 *    VITALS (top-left)      SCORE (top-centre)            LOCATION + ROUTE (right edge)
 *    hull shield energy     the score, the chain          the loka, the region, the yojana,
 *    dash, lives, purse     draining under it             and a ruler of the whole climb
 *
 *    RADAR (bottom-left)    ARMAMENT CONSOLE (bottom-centre)
 *    the dial, north up     the fitted weapon, and the Astra cells beside it
 *  </pre>
 *
 *  Over the run: the victory banner and the results card. In the hangar: nothing but the hangar.
 *  Co-op adds a CREW slab under the vitals.
 *
 *  <p>Each frame runs in three steps, and the order is the point:
 *  <ol>
 *   <li>{@link #refresh}: rebuild every cached string and measurement whose value moved. Nothing
 *       after this step formats or measures anything it does not have to.</li>
 *   <li>shape pass: every plate, bar and mark (a ShapeRenderer cannot live inside an open batch).</li>
 *   <li>batch pass: art and words.</li>
 *  </ol>
 *  Because refresh comes first, a shape that depends on a measured string never lags a frame
 *  behind it. No key bindings are drawn here except the one that matters (F, when an Astra
 *  release is in hand); the control screen is the key reference. */
public final class Hud {
    private final Ui ui;
    private final HudBars bars = new HudBars();            // the vitals slab
    private final HudRoute route = new HudRoute();         // the right edge
    private final WeaponCards cards = new WeaponCards();   // the 15 weapon face cards
    private final ShardGauge shards;                       // the Astra cells
    private final HudCompass compass;                      // the radar dial

    // --- THE SCORE SLAB (top-centre) ---
    public static final float SCORE_W = 252, SCORE_H = 50, CHAIN_H = 28;

    // --- THE ROUTE and the LOCATION TAG (right edge). Public: the screenshot test samples them. ---
    public static final float ROUTE_X = HudRoute.RX, ROUTE_TOP = HudRoute.RTOP, ROUTE_BOT = HudRoute.RBOT;
    public static final float LOC_X = HudRoute.LOC_X, LOC_W = HudRoute.LOC_W, LOC_H = HudRoute.LOC_H,
            LOC_TOP = HudRoute.LOC_TOP;

    // --- THE ARMAMENT CONSOLE (bottom-centre): the fitted weapon, then the Astra cells. ---
    public static final float WEAPON_W = 270, CONSOLE_H = 68, CONSOLE_Y = 14, CONSOLE_PAD = 18;
    public static final float CARD = 46;

    /** The console's width: the weapon, then a cell per element this loka pays. */
    public static float consoleW(World w) {
        return WEAPON_W + CONSOLE_PAD + ShardGauge.width(ShardGauge.paidElements(w).length) + CONSOLE_PAD;
    }

    public static float consoleX(World w) {
        return (Ui.UI_W - consoleW(w)) / 2f;
    }

    // --- THE CREW: co-op pilots under the vitals, each row their seat colour's swatch and name,
    //     then their round-trip ping, green when quick and clay-red when slow. Your own row
    //     reads "-" (a delay to yourself is not a thing). Hidden in single-player, the hangar and
    //     the results card. ---
    public static final float CREW_X = HudBars.X, CREW_TOP = HudBars.BOT - 10, CREW_PITCH = 20;
    public static final float CREW_W = 200;
    private static final float CREW_NAME_W = 100;
    private static final float CREW_PING_R = CREW_X + CREW_W - 12;

    // --- RESULTS CARD: every run (death, the blade whole, Bala fallen) ends on one framed card.
    //     Death and the blade show it at once; a won run plays its 4 s banner lap first, exactly
    //     when GameScreen freezes the world. ---
    private static final float END_X = Template.COL - 320, END_Y = 270, END_W = 640, END_H = 250;
    private static final float END_TITLE_Y = 478, END_SUB_Y = 440;
    private static final float[] END_LINES = { 396, 366, 336 };

    /** Yojana reading: depth below the grand gate, so 0 at the gate and 10,000 at the ceiling.
     *  Bhu Loka (below the gate) clamps to 0. Atala's 19,000 px is its canonical 10,000 yojanas
     *  (Bhagavata 5.24.7, Vishnu Purana: Atala is the first of the seven lower lokas). */
    static final float YOJANA_PX = 1.9f;

    static int yojanaAt(float worldY) {
        return (int) Math.round(Math.max(0, worldY - World.ATALA_Y) / YOJANA_PX);
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
     *  over it: vitals, score, the route and the console are all noise on a menu screen.
     *  Game over still gets a translucent dimmer, since there the frozen world IS the point. */
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
            Puranic.c(ui, Puranic.NIGHT, 0.82f);
            ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H);
        }
        if (over) { // the results card: a clay plate with the panel edge
            Puranic.plate(ui, END_X, END_Y, END_W, END_H, 0.97f, 1f);
        }
        if (hangar) {
            shopUi.drawBackground(w);
            return;
        }
        shards.update(w);
        bars.shapes(ui, w);
        crewShapes(w);
        scoreShapes();
        route.shapes(ui, w);
        compass.shapes(ui.shapes, w);
        consoleShapes(w);
    }

    private void batchPass(SpriteBatch batch, World w, boolean hangar, boolean over,
                           ShipRenderer ships, ShopUi shopUi) {
        if (!hangar) {
            bars.text(batch, ui, w);
            drawCrew(batch, w);
            scoreText(batch);
            route.text(batch, ui, w, lokaStr, regStr, yojStr);
            compass.label(batch, w);
            consoleText(batch, w);
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
     *  to do about it, otherwise the crew block simply vanishes and the pilot is stranded. */
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
    private ShipDef shownHull;
    private float shownDm = -1, shownCm = -1;
    private String weaponStr = "", dpsStr = "";

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
            comboStr = w.score.combo >= 2 ? "CHAIN " + w.score.combo + " ×" + w.score.mult() : "";
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
                yojStr = String.format(Locale.ROOT, "%,d", yoj);
            }
        }

        int wi = w.hangar.weaponIndex;
        float dm = w.hangar.damageMult(w.ship.def), cm = w.hangar.cooldownMult(w.ship.def);
        if (wi != shownWeapon || w.ship.def != shownHull || dm != shownDm || cm != shownCm) {
            shownWeapon = wi;
            shownHull = w.ship.def;
            shownDm = dm;
            shownCm = cm;
            weaponStr = WeaponDef.ALL[wi].name.toUpperCase(Locale.ROOT);
            dpsStr = "DPS " + Math.round(ShopStats.shot(w.hangar, WeaponDef.ALL[wi]).dps);
        }

        refreshCrew(w);
    }

    // --- THE CREW: names read off the session roster, pings off the session's measures.
    //     Rebuilt only when the session's epoch moves (roster churn or a fresh measure):
    //     by design the block never touches String.format or measure() in the hot loop. ---

    private long crewEpoch = -1;
    private int[] crewIds = new int[0];
    private String[] crewNames = new String[0];
    private static final int CREW_PING_GREEN = 80; // ms: at/below reads green, to 160 amber, above red

    private int leftSeen;
    private String leftNote = "";
    private long leftUntil;

    /** A pilot who leaves is announced once, centre screen, for four seconds. */
    private void watchLeavers() {
        vajra.net.Session s = Net.session;
        if (s == null || !Net.active()) return;
        if (s.leftCount() != leftSeen) {
            leftSeen = s.leftCount();
            leftNote = s.lastLeft().toUpperCase(Locale.ROOT) + " LEFT THE RUN";
            leftUntil = System.currentTimeMillis() + 4000;
        }
    }

    private void refreshCrew(World w) {
        watchLeavers();
        vajra.net.Session s = Net.session;
        if (s == null || !Net.active() || w.runEnded()
                || (s.epoch() == crewEpoch && crewNames.length == s.count())) return;
        crewEpoch = s.epoch();
        crewIds = new int[s.count()];
        crewNames = new String[s.count()];
        for (int i = 0; i < s.count(); i++) {
            crewIds[i] = s.member(i).id;
            // the name is trimmed to its column HERE, once per roster change; the draw loop
            // must not measure text every frame
            crewNames[i] = fitName(s.member(i).name);
        }
    }

    /** A pilot's name, trimmed to the column it lives in: the name is theirs, but the column
     *  is the column's. A long name is cut, not allowed to run into the ping. */
    private String fitName(String name) {
        if (ui.measure(ui.small, name) <= CREW_NAME_W) return name;
        String cut = name;
        while (cut.length() > 1 && ui.measure(ui.small, cut + "..") > CREW_NAME_W)
            cut = cut.substring(0, cut.length() - 1);
        return cut + "..";
    }

    private boolean crewShown(World w) {
        vajra.net.Session s = Net.session;
        return s != null && Net.active() && !w.runEnded() && crewIds.length > 0;
    }

    /** The crew slab: one seat swatch per pilot. */
    private void crewShapes(World w) {
        if (!crewShown(w)) return;
        float h = crewIds.length * CREW_PITCH + 14;
        Puranic.slab(ui, CREW_X, CREW_TOP - h, CREW_W, h, 1f);
        for (int i = 0; i < crewIds.length; i++) {
            float y = CREW_TOP - 17 - i * CREW_PITCH;
            ui.shapes.setColor(SeatColors.of(crewIds[i]));
            ui.shapes.rect(CREW_X + 12, y - 4, 9, 9);
        }
    }

    /** The crew's words: the name in their seat hue, the ping green when quick, clay-red when
     *  slow, "-" until measured. Your own row reads "-". */
    private void drawCrew(SpriteBatch batch, World w) {
        if (!crewShown(w)) return;
        vajra.net.Session s = Net.session;
        float cap = ui.small.getCapHeight() / 2f;
        for (int i = 0; i < crewIds.length; i++) {
            float y = CREW_TOP - 17 - i * CREW_PITCH;
            ui.small.setColor(SeatColors.of(crewIds[i]));
            ui.small.draw(batch, crewNames[i], CREW_X + 28, y + cap);
            boolean me = crewIds[i] == s.myId();
            if (me && s.isHost()) { // the host is the hub: there is no round trip to itself
                ui.small.setColor(Color.LIGHT_GRAY);
                ui.right(batch, ui.small, "HOST", CREW_PING_R, y + cap);
                continue;
            }
            int p = me ? s.pingOf(s.myId()) : s.pingTo(crewIds[i]); // my own row shows MY ping to the hub
            ui.small.setColor(p <= 0 ? Color.GRAY : p <= CREW_PING_GREEN ? SeatColors.GO
                    : p <= 160 ? Color.GOLD : SeatColors.WAIT);
            ui.right(batch, ui.small, p > 0 ? p + " MS" : "...", CREW_PING_R, y + cap);
        }
        if (System.currentTimeMillis() < leftUntil) { // someone left: say so, once
            ui.small.setColor(SeatColors.WAIT);
            ui.centered(batch, ui.small, leftNote, Ui.UI_H - 92);
        }
        ui.small.setColor(Color.WHITE);
    }

    /** The results are built once per run, not once per frame (they format several numbers) and
     *  rebuilt if the score, best or purse moves, since the best is persisted around the moment
     *  the run ends. */
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

    /** The combo window left, read from the world each frame (it drains continuously, so it is
     *  the one number here that cannot be cached). */
    private float comboT;

    private boolean chaining() {
        return !comboStr.isEmpty();
    }

    /** The score slab's top edge hugs the screen top; it grows a row when a chain is running. */
    private float scoreH() {
        return SCORE_H + (chaining() ? CHAIN_H : 0);
    }

    private void scoreShapes() {
        float x = (Ui.UI_W - SCORE_W) / 2f, top = Ui.UI_H - 14, h = scoreH();
        Puranic.slab(ui, x, top - h, SCORE_W, h, 1f);
        if (chaining()) { // the chain: a meter that drains with the combo window
            float k = Math.max(0f, comboT / Score.WINDOW);
            Puranic.meter(ui, x + 16, top - h + 7, SCORE_W - 32, 4, k, Puranic.OCHRE, 0.35f + 0.65f * k);
        }
    }

    /** Top-centre: the ten-digit score in the display numerals; the chain under it while it runs,
     *  its brightness draining with the combo window: the fade is the urgency cue. */
    private void scoreText(SpriteBatch batch) {
        float top = Ui.UI_H - 14;
        ui.big.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
        ui.centeredSpaced(batch, ui.big, scoreStr, top - 25 + ui.big.getCapHeight() / 2f - 2, 2f);
        ui.big.setColor(1, 1, 1, 1);
        if (chaining()) {
            float a = 0.5f + 0.5f * Math.max(0f, comboT / Score.WINDOW);
            ui.num.setColor(Puranic.OCHRE[0], Puranic.OCHRE[1], Puranic.OCHRE[2], a);
            ui.centered(batch, ui.num, comboStr, top - 52 + ui.num.getCapHeight() / 2f);
            ui.num.setColor(1, 1, 1, 1);
        }
    }

    /** The console: one slab, the weapon on its left, a rule, the Astra cells on its right. A
     *  pulsing ochre edge when a release is in hand. */
    private void consoleShapes(World w) {
        float x0 = consoleX(w), cw = consoleW(w);
        Puranic.slab(ui, x0, CONSOLE_Y, cw, CONSOLE_H, 1f);
        Puranic.c(ui, Puranic.COPPER, 0.5f);
        ui.shapes.rect(x0 + WEAPON_W, CONSOLE_Y + 12, 1, CONSOLE_H - 24);
        // the weapon's face on a small dark plate
        Puranic.c(ui, Puranic.NIGHT, 0.9f);
        Puranic.pill(ui, x0 + 14, CONSOLE_Y + (CONSOLE_H - CARD) / 2f - 3, CARD + 6, CARD + 6, 5);
        Puranic.c(ui, Puranic.COPPER, 0.6f);
        Puranic.arcFrame(ui, x0 + 14, CONSOLE_Y + (CONSOLE_H - CARD) / 2f - 3, CARD + 6, CARD + 6, 5, 1f);
        shards.shapes(w, x0 + WEAPON_W + CONSOLE_PAD, CONSOLE_Y + 4, CONSOLE_H - 20);
        if (w.hangar.forgeCharges[w.lokaIndex] > 0) {
            Puranic.c(ui, Puranic.OCHRE, 0.55f + 0.45f * (float) Math.sin(Puranic.time() * 5f));
            Puranic.arcFrame(ui, x0 - 1.5f, CONSOLE_Y - 1.5f, cw + 3, CONSOLE_H + 3, 7, 2f);
        }
    }

    private void consoleText(SpriteBatch batch, World w) {
        float x0 = consoleX(w), y = CONSOLE_Y;
        batch.draw(cards.get(shownWeapon), x0 + 17, y + (CONSOLE_H - CARD) / 2f, CARD, CARD);
        ui.body.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
        ui.body.draw(batch, weaponStr, x0 + 14 + CARD + 18, y + CONSOLE_H / 2f + 14 + ui.body.getCapHeight() / 2f);
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        ui.small.draw(batch, dpsStr, x0 + 14 + CARD + 18, y + CONSOLE_H / 2f - 12 + ui.small.getCapHeight() / 2f);
        boolean ready = w.hangar.forgeCharges[w.lokaIndex] > 0;
        String label = w.winding() ? "RELEASING" : ready ? "ASTRA READY   F" : "ASTRA";
        if (ready || w.winding()) ui.small.setColor(Puranic.OCHRE[0], Puranic.OCHRE[1], Puranic.OCHRE[2], 1f);
        ui.small.draw(batch, label, x0 + WEAPON_W + CONSOLE_PAD, y + CONSOLE_H - 8 + ui.small.getCapHeight() / 2f - 4);
        ui.small.setColor(1, 1, 1, 1);
        ui.body.setColor(1, 1, 1, 1);
        shards.draw(batch, w, x0 + WEAPON_W + CONSOLE_PAD, y + 4, CONSOLE_H - 20);
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
        Template.line(ui, batch, "R or click for a new run", END_LINES[2] - 40, Template.A_HINT);
    }

    public void dispose() {
        cards.dispose();
        shards.dispose();
    }
}
