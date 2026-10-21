package vajra.screen;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.LokaTable;
import vajra.world.World;

import java.util.ArrayList;

/** THE ARMOURY OF ATALA — the hangar, dressed as the forge Maya built under Bala's rule. One opaque screen: a rail of sections on the left, one page on the right.
 *
 *  <p>RAIL (grouped, TAB / click to switch):
 *  <pre>
 *    BAY                      the dashboard: three cards and a NEXT UP table
 *    SHIPS   FLEET            a grid of hull cards, a stat table against the hull in hand
 *            REFIT            a matrix: every hull a row, every upgrade tier a column
 *    ARMS    PATTERNS         a table of every pattern, and a detail card
 *    ASTRA   FORGE            one card per loka, and the release of the current one
 *            VAULT            shards by element, and SELL ALL
 *  </pre>
 *
 *  <p>LOOK — a Puranic palette: deep indigo for the dark, dark brass for hairlines and quiet
 *  type, saffron for "selected" or "pressing the key would do something", lotus cream for the
 *  words. Peacock green marks a gain and rose a loss, and only on a number or a chart. The
 *  charts ({@link ShopStats}) are radars for hulls and bars for patterns, so every purchase
 *  shows what it changes before it is bought. The only ornament is four corner ticks on a card. Coin and
 *  shard amounts are always their art plus a number. Every page ends in ONE answer line above the
 *  keys line.
 *
 *  <p>MOTION — none. Selection changes are instant; {@link #update(float)} is kept as a no-op so
 *  existing callers still compile.
 *
 *  <p>INPUT (for {@link ShopController}): {@link #move(int, int)} takes the direction keys (dx:
 *  -1 left / +1 right, dy: -1 up / +1 down) and does the right thing on every page.
 *  {@link #stepShip}/{@link #stepWeapon}/{@link #scroll} remain for the old call sites. The
 *  pointer only HIGHLIGHTS on hover; a click selects.
 *
 *  <p>STRUCTURE — this file owns layout, hit boxes and every label; the controller owns only what a
 *  pressed region DOES. {@code click} reports {@link #hitRegion} / {@link #hitRow}:
 *  TAB = section index, HULL = hull index, TIER = tier 0..2 (the hull is then {@link #ship()}),
 *  WEAPON = pattern index, ASTRA = 0, SELL, CLOSE.
 */
public final class ShopUi {
    // =============================================================== frame
    public static final float CX = Ui.UI_W / 2f;
    /** The outer rails: the header rule, the rail and the wallet all hang off these. */
    public static final float M0 = 42, M1 = Ui.UI_W - 42;
    public static final float HEAD_Y = UiTheme.SCREEN_H - 72;
    public static final float HEAD_RULE_Y = HEAD_Y - 26;

    // =============================================================== rail
    public static final float RAIL_X = M0, DIV_X = 230;

    // =============================================================== body
    public static final float BODY_X = 262, BODY_W = M1 - BODY_X;
    public static final float BODY_TOP = HEAD_RULE_Y - 26, BODY_Y = 112;
    public static final float BODY_H = BODY_TOP - BODY_Y;
    public static final float BODY_CX = BODY_X + BODY_W / 2f;
    /** Rule above the answer line; ANSWER_Y is the answer line's centre. */
    public static final float ANSWER_RULE_Y = 94, ANSWER_Y = 66, FOOTER_Y = 26;

    // --- the wallet: one right-anchored instrument line on the header row ---
    public static final float WALLET_Y = HEAD_Y, WALLET_H = 30, WALLET_GAP = 12;
    public static final float WALLET_X1 = Ui.UI_W - 42;
    public static final float WALLET_COINS_W = 76, WALLET_SHARDS_W = 64, WALLET_SELL_W = 160;
    public static final float WALLET_SELL_X = WALLET_X1 - WALLET_COINS_W - WALLET_GAP
            - WALLET_SHARDS_W - WALLET_GAP - WALLET_SELL_W;

    // =============================================================== sections
    public static final int TAB_BAY = 0, TAB_FLEET = 1, TAB_REFIT = 2, TAB_PATTERNS = 3,
            TAB_FORGE = 4, TAB_VAULT = 5;
    /** The old three names still point at their nearest section. */
    public static final int TAB_HULL = TAB_FLEET, TAB_PATTERN = TAB_PATTERNS, TAB_ASTRA = TAB_FORGE;
    public static final String[] TAB_NAMES = { "WAR TABLE", "HULLS", "REFORGING", "WEAPONS", "ASTRA FORGE", "SHARDS" };

    // clickable regions
    /** What a point is over while the confirmation is up. */
    public static final int CONFIRM_OUTSIDE = -1, CONFIRM_INSIDE = 0, CONFIRM_BUY = 1, CONFIRM_CANCEL = 2;
    public static final int NONE = -1, TAB = 0, HULL = 1, TIER = 2, WEAPON = 3, ASTRA = 4,
            SELL = 5, CLOSE = 6;
    /** One hit region: shards exist to forge the Astra, and only the loka's own element pays it. */
    public static final int ASTRA_ROWS = 1;

    // --- rail layout: offsets down from BODY_TOP, by section; group headings between ---
    private static final float[] RAIL_DY = { 16, 90, 126, 200, 274, 310 };
    private static final String[] GROUP_NAMES = { "SHIPS", "ARMS", "ASTRA" };
    private static final float[] GROUP_DY = { 60, 170, 244 };

    // --- BAY ---
    private static final float BAY_CW = 300, BAY_CH = 276;
    private static final float BAY_GAP = (BODY_W - 3 * BAY_CW) / 2f;
    private static final float NEXT_PITCH = 24;

    // --- FLEET ---
    private static final int FLEET_COLS = 4;
    private static final float FLEET_CW = 226, FLEET_CH = 122, FLEET_RGAP = 14;
    private static final float FLEET_GAP = (BODY_W - FLEET_COLS * FLEET_CW) / (FLEET_COLS - 1);

    /** The radar's size on the HULLS page and the REFORGING page. */
    private static final float RADAR_R = 58, REFIT_RADAR_R = 46;

    // --- REFIT ---
    private static final float REFIT_NAME_W = 214, REFIT_PITCH = 40;
    private static final float REFIT_COL_W = (BODY_W - REFIT_NAME_W) / 3f;

    // --- PATTERNS ---
    private static final float PAT_TABLE_W = 628;
    private static final float PAT_CARD_X = BODY_X + PAT_TABLE_W + 36;
    private static final float PAT_CARD_W = M1 - PAT_CARD_X;
    private static final float PAT_CARD_Y0 = 176;
    private static final float PAT_CHART_PITCH = 31;

    // --- FORGE ---
    private static final float FORGE_CH = 232, FORGE_GAP = 14, FORGE_MAXW = 190;

    // --- VAULT ---
    private static final float VAULT_PITCH = 40, VAULT_BTN_W = 420, VAULT_BTN_H = 44;

    private static final String[] STAT_NAMES = { "HP", "SPEED", "TURN" };

    // =============================================================== palette (Puranic)
    private static final float[] SAF = { 1.00f, 0.62f, 0.14f };      // saffron: selected, actionable
    private static final float[] BRASS = { 0.80f, 0.62f, 0.30f };    // dark brass: hairlines, quiet type
    private static final float[] CREAM = { 0.97f, 0.92f, 0.80f };    // lotus cream: the words
    private static final float[] PEACOCK = { 0.36f, 0.86f, 0.74f };  // a gain
    private static final float[] ROSE = { 0.96f, 0.45f, 0.50f };     // a loss
    private static final float[] LAVENDER = { 0.64f, 0.62f, 1.00f }; // the hull you are flying
    private static final float[] DEEP = { 0.030f, 0.025f, 0.075f };  // indigo, the dark end
    private static final float[] INDIGO = { 0.070f, 0.055f, 0.150f }; // indigo, the lit end (HudScreenshotTest wants the corner within 16 of ink)
    private static final float[] PANEL = { 0.105f, 0.085f, 0.250f }; // a card's fill

    private final Ui ui;
    private final GlyphLayout gl = new GlyphLayout();
    private final float maxHp, maxSpd, maxTurn; // the best base value across hulls: a full stat bar

    private int tab = TAB_BAY;
    private int ship, tier, weapon;
    private int hitRegion = NONE, hitRow = NONE;

    // hover highlight (never moves the cursor) and the last probe's result
    private int hoverRegion = NONE, hoverRow = NONE, hoverSub = NONE;
    private int probeRow = NONE, probeSub = NONE;
    // cursor jumps a probe wants applied on click (BAY cards and NEXT UP rows, REFIT cells)
    private int jShip = NONE, jTier = NONE, jWeapon = NONE;

    private World lastWorld;
    private int curLoka = -1;
    private float cap = 12; // cap height of the hud font, measured each frame

    /** One row of the BAY's NEXT UP table, rebuilt every frame from the hangar. */
    private static final class Next {
        String kind = "", what = "", costText;
        int coins, shards, dest, jShip = NONE, jTier = NONE, jWeapon = NONE;
        boolean ready;
    }
    /** A purchase waiting on a yes: what it is, what it costs, and what is left after. */
    public static final class Confirm {
        public static final int HULL = 0, TIER = 1, WEAPON = 2;
        public final int kind;
        public final String title, name, effect;
        public final int ship, tier, weapon, coins, shards, coinsLeft, shardsLeft;

        private Confirm(int kind, String title, String name, String effect, int ship, int tier,
                        int weapon, int coins, int shards, int coinsLeft, int shardsLeft) {
            this.kind = kind;
            this.title = title;
            this.name = name;
            this.effect = effect;
            this.ship = ship;
            this.tier = tier;
            this.weapon = weapon;
            this.coins = coins;
            this.shards = shards;
            this.coinsLeft = coinsLeft;
            this.shardsLeft = shardsLeft;
        }

        static Confirm hull(ShipDef d, int cost, int coinsLeft) {
            return new Confirm(HULL, "BUY THIS HULL?", d.name, ShopStats.role(d), d.id, 0, -1,
                    cost, 0, coinsLeft, -1);
        }

        static Confirm tier(ShipDef d, int tier, int cost, int shards, int coinsLeft, int shardsLeft) {
            ShipDef.Upgrade u = d.upgrades[tier];
            return new Confirm(TIER, "BUY THIS UPGRADE?", u.label + "  " + u.name + "  on " + d.name,
                    tierEffect(d, tier), d.id, tier, -1, cost, shards, coinsLeft, shardsLeft);
        }

        static Confirm weapon(Hangar h, WeaponDef w, int coinsLeft) {
            ShopStats.Shot s = ShopStats.shot(h, w);
            return new Confirm(WEAPON, "BUY THIS WEAPON?", w.name,
                    "DPS " + Math.round(s.dps) + "   REACH " + Math.round(s.reach) + " px",
                    -1, 0, w.idx, w.cost, 0, coinsLeft, -1);
        }
    }

    private Confirm confirm;
    private int confirmHover = CONFIRM_OUTSIDE;
    private Texture pixel; // one white texel: the modal's rects ride the batch, over every page's text

    private final Next[] next = { new Next(), new Next(), new Next(), new Next() };
    private int nextCount;

    public ShopUi(Ui ui) {
        this.ui = ui;
        float mh = 1, ms = 1, mt = 1;
        for (ShipDef d : ShipDef.ALL) {
            mh = Math.max(mh, d.maxHp);
            ms = Math.max(ms, d.maxSpeed);
            mt = Math.max(mt, d.turnRate);
        }
        maxHp = mh;
        maxSpd = ms;
        maxTurn = mt;
    }

    // =============================================================== cursors
    public int tab() { return tab; }
    public int ship() { return ship; }
    public int tier() { return tier; }
    public int weapon() { return weapon; }
    public void setTab(int t) { tab = Math.floorMod(t, TAB_NAMES.length); }
    public void setShip(int i) { if (i >= 0 && i < ShipDef.ALL.length) ship = i; }
    public void setTier(int t) { tier = Math.floorMod(t, 3); }
    public void setWeapon(int i) { if (i >= 0 && i < WeaponDef.ALL.length) weapon = i; }

    /** Hull step, wrapping. */
    public void stepShip(int d) { ship = Math.floorMod(ship + d, ShipDef.ALL.length); }

    /** Pattern step, wrapping. */
    public void stepWeapon(int d) { weapon = Math.floorMod(weapon + d, WeaponDef.ALL.length); }

    /** Direction keys (WASD and arrows): dx -1 left / +1 right, dy -1 up / +1 down. */
    public void move(int dx, int dy) {
        int n = ShipDef.ALL.length;
        switch (tab) {
            case TAB_FLEET:
                if (dx != 0) ship = Math.floorMod(ship + dx, n);
                if (dy != 0) {
                    int t = ship + dy * FLEET_COLS;
                    if (t >= 0 && t < n) ship = t;
                    else if (t >= n && ship / FLEET_COLS < (n - 1) / FLEET_COLS) ship = n - 1;
                }
                break;
            case TAB_REFIT:
                if (dx != 0) tier = Math.floorMod(tier + dx, 3);
                if (dy != 0) ship = Math.floorMod(ship + dy, n);
                break;
            case TAB_PATTERNS: {
                int d = dy != 0 ? dy : dx;
                if (d != 0) stepWeapon(d);
                break;
            }
            default:
                break;
        }
    }

    /** Mouse wheel: scrolls whichever list is on screen. */
    public void scroll(float amountY) {
        int d = amountY > 0 ? 1 : amountY < 0 ? -1 : 0;
        if (tab == TAB_FLEET || tab == TAB_REFIT) stepShip(d);
        else if (tab == TAB_PATTERNS) stepWeapon(d);
    }

    /** Open on whatever the run is flying, on the bay. */
    public void syncTo(Hangar h) {
        setShip(h.shipIndex);
        setWeapon(h.weaponIndex);
        tier = 0;
        tab = TAB_BAY;
    }

    /** No motion on this screen; kept so existing callers still compile. */
    public void update(float dt) { }

    // =============================================================== geometry
    /** Baseline of rail section i — draw, probe and the screenshot test all sample this. */
    public static float railY(int i) { return BODY_TOP - RAIL_DY[i]; }

    private static float bayX(int c) { return BAY_CW * c + BAY_GAP * c + BODY_X; }
    private static float nextHeadY() { return BODY_TOP - BAY_CH - 30; }
    /** The quest strip's heading line, under the NEXT UP rows. */
    private static float questY() { return 130; }
    private static float nextRowY(int r) { return nextHeadY() - 16 - 20 - r * NEXT_PITCH; }

    private static float fleetX(int i) { return BODY_X + (i % FLEET_COLS) * (FLEET_CW + FLEET_GAP); }
    private static float fleetTop(int i) { return BODY_TOP - (i / FLEET_COLS) * (FLEET_CH + FLEET_RGAP); }
    private static float fleetBottom(int n) {
        int rows = (n + FLEET_COLS - 1) / FLEET_COLS;
        return BODY_TOP - (rows - 1) * (FLEET_CH + FLEET_RGAP) - FLEET_CH;
    }
    private static float fleetHeadY(int n) { return fleetBottom(n) - 36; }
    private static float fleetStatY(int n, int k) { return fleetHeadY(n) - 28 - k * 25; }
    private static float fleetRadarX() { return BODY_X + 150; }
    private static float fleetRadarY(int n) { return (fleetBottom(n) - 24 + BODY_Y + 14) / 2f + 14; }
    private static float fleetTableX() { return BODY_X + 330; }
    private static float fleetUpgX() { return BODY_X + 700; }

    private static float refitColX(int t) { return BODY_X + REFIT_NAME_W + t * REFIT_COL_W; }
    private static float refitRowY(int i) { return BODY_TOP - 54 - i * REFIT_PITCH; }
    private static float refitBottom(int n) { return refitRowY(n - 1) - REFIT_PITCH / 2f; }
    private static float refitRadarX() { return BODY_X + 150; }
    private static float refitRadarY(int n) { return (refitBottom(n) - 34 + BODY_Y) / 2f - 2; }
    private static float refitTableX() { return BODY_X + 300; }
    private static float refitTableY(int n, int k) { return refitBottom(n) - 54 - k * 20; }

    /** Centre line of metric row i in the pattern card; its bar sits 13 below. */
    private static float patChartY(int i) { return BODY_TOP - 232 - i * PAT_CHART_PITCH; }

    private static float patPitch(int n) { return Math.min(28f, (BODY_TOP - 34 - BODY_Y) / n); }
    private static float patRowY(int i, int n) {
        float p = patPitch(n);
        return BODY_TOP - 34 - p / 2f - i * p;
    }

    private static float forgeCardW(int n) {
        return Math.min(FORGE_MAXW, (BODY_W - FORGE_GAP * (n - 1)) / n);
    }
    private static float forgeX(int l, int n) {
        float cw = forgeCardW(n);
        float row = n * cw + (n - 1) * FORGE_GAP;
        return BODY_X + (BODY_W - row) / 2f + l * (cw + FORGE_GAP);
    }
    private static float forgeRuleY() { return BODY_TOP - FORGE_CH - 22; }

    private static int vaultRows() { return World.TATVA.length; }
    private static float vaultRowY(int i) { return BODY_TOP - 54 - i * VAULT_PITCH; }
    private static float vaultRuleY(int n) { return vaultRowY(n - 1) - VAULT_PITCH / 2f; }
    private static float vaultTotalY(int n) { return vaultRuleY(n) - 24; }
    private static float vaultBtnY(int n) { return vaultTotalY(n) - 64; }

    // =============================================================== the confirmation
    private static final float CONF_W = 460, CONF_H = 226, CONF_BTN_W = 176, CONF_BTN_H = 34;
    private static final float CONF_X0 = CX - CONF_W / 2f, CONF_Y0 = UiTheme.SCREEN_H / 2f - CONF_H / 2f;
    private static final float CONF_BTN_Y = CONF_Y0 + 36;

    public void setConfirm(Confirm c) {
        confirm = c;
        confirmHover = CONFIRM_OUTSIDE;
    }

    public Confirm confirm() { return confirm; }

    /** What is under the pointer while the confirmation is up. */
    public int confirmHit(Vector2 m) {
        if (Math.abs(m.y - CONF_BTN_Y) <= CONF_BTN_H / 2f) {
            if (Math.abs(m.x - (CX - 100)) <= CONF_BTN_W / 2f) return CONFIRM_BUY;
            if (Math.abs(m.x - (CX + 100)) <= CONF_BTN_W / 2f) return CONFIRM_CANCEL;
        }
        boolean in = m.x >= CONF_X0 && m.x <= CONF_X0 + CONF_W && m.y >= CONF_Y0 && m.y <= CONF_Y0 + CONF_H;
        return in ? CONFIRM_INSIDE : CONFIRM_OUTSIDE;
    }

    public void confirmHover(Vector2 m) {
        confirmHover = confirmHit(m);
    }

    /** A filled rect in the batch pass, in a tone. */
    private void box(SpriteBatch b, float x, float y, float w, float h, float[] c, float a) {
        b.setColor(c[0], c[1], c[2], a);
        b.draw(pixel, x, y, w, h);
        b.setColor(1, 1, 1, 1);
    }

    private void outline(SpriteBatch b, float x, float y, float w, float h, float[] c, float a) {
        box(b, x, y, w, 1, c, a);
        box(b, x, y + h - 1, w, 1, c, a);
        box(b, x, y, 1, h, c, a);
        box(b, x + w - 1, y, 1, h, c, a);
    }

    /** The modal: one scrim, one plate, a question, a price and two buttons. Drawn last, in the
     *  batch pass, so it lies over every page's text. */
    private void drawConfirm(SpriteBatch b, Hangar h, int el) {
        if (confirm == null) return;
        if (pixel == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(1, 1, 1, 1);
            pm.fill();
            pixel = new Texture(pm);
            pm.dispose();
        }
        Confirm c = confirm;
        box(b, 0, 0, Ui.UI_W, Ui.UI_H, DEEP, 0.78f);                       // the page steps back
        box(b, CONF_X0, CONF_Y0, CONF_W, CONF_H, PANEL, 1f);               // the plate
        outline(b, CONF_X0, CONF_Y0, CONF_W, CONF_H, BRASS, 0.7f);
        box(b, CONF_X0, CONF_Y0 + CONF_H - 3, CONF_W, 3, SAF, 1f);         // a saffron lintel

        float top = CONF_Y0 + CONF_H;
        tinted(b, c.title, CX, top - 28, 0, BRASS, 1f);
        centerGold(b, c.name, CX, top - 58, 1f);
        tinted(b, c.effect, CX, top - 82, 0, CREAM, UiTheme.A_LINE);
        box(b, CONF_X0 + 24, top - 98, CONF_W - 48, 1, BRASS, 0.3f);
        priceCenter(b, CX, top - 120, c.coins, c.shards, el, "", true, 1f);
        String left = "LEAVES YOU " + c.coinsLeft + (c.coinsLeft == 1 ? " COIN" : " COINS")
                + (c.shards > 0 ? " AND " + c.shardsLeft + (c.shardsLeft == 1 ? " SHARD" : " SHARDS") : "");
        tinted(b, left, CX, top - 144, 0, BRASS, 1f);

        buttonBox(b, CX - 100, "ENTER   BUY", true, confirmHover == CONFIRM_BUY);
        buttonBox(b, CX + 100, "ESC   CANCEL", false, confirmHover == CONFIRM_CANCEL);
    }

    private void buttonBox(SpriteBatch b, float cx, String label, boolean primary, boolean hot) {
        float x = cx - CONF_BTN_W / 2f, y = CONF_BTN_Y - CONF_BTN_H / 2f;
        float[] tone = primary ? SAF : BRASS;
        if (primary || hot) box(b, x, y, CONF_BTN_W, CONF_BTN_H, tone, hot ? 0.34f : 0.2f);
        outline(b, x, y, CONF_BTN_W, CONF_BTN_H, tone, hot || primary ? 1f : 0.7f);
        if (primary) put(b, label, cx, CONF_BTN_Y, 0, 1f, true);
        else put(b, label, cx, CONF_BTN_Y, 0, hot ? 1f : UiTheme.A_LINE, false);
    }

    // =============================================================== hit testing
    /** Highlights only — the cursor does not move. Returns whether an ACTION is under the pointer. */
    public boolean hover(Vector2 m) {
        int region = probe(m);
        hoverRegion = region;
        hoverRow = probeRow;
        hoverSub = probeSub;
        return region != NONE;
    }

    public boolean click(Vector2 m) {
        hitRegion = NONE;
        hitRow = NONE;
        int region = probe(m);
        if (region != NONE) {
            if (region == HULL) ship = probeRow;
            else if (region == TIER) {
                tier = probeRow;
                if (jShip >= 0) ship = jShip;
            } else if (region == WEAPON) weapon = probeRow;
            else if (region == TAB) {
                tab = Math.floorMod(probeRow, TAB_NAMES.length);
                if (jShip >= 0) setShip(jShip);
                if (jTier >= 0) setTier(jTier);
                if (jWeapon >= 0) setWeapon(jWeapon);
            }
            hitRegion = region;
            hitRow = region == SELL ? NONE : probeRow;
            return true;
        }
        if (m.y > HEAD_RULE_Y) { hitRegion = CLOSE; return true; } // the close strip
        return false;
    }

    public int hitRegion() { return hitRegion; }
    public int hitRow() { return hitRow; }

    /** What is under `m`. Sets probeRow / probeSub / the jump fields; mutates nothing else. */
    private int probe(Vector2 m) {
        probeRow = NONE;
        probeSub = NONE;
        jShip = jTier = jWeapon = NONE;
        for (int i = 0; i < TAB_NAMES.length; i++)
            if (m.x >= RAIL_X && m.x <= DIV_X - 8 && Math.abs(m.y - railY(i)) <= 16) {
                probeRow = i;
                return TAB;
            }
        int r;
        switch (tab) {
            case TAB_BAY: r = probeBay(m); break;
            case TAB_FLEET: r = probeFleet(m); break;
            case TAB_REFIT: r = probeRefit(m); break;
            case TAB_PATTERNS: r = probePatterns(m); break;
            case TAB_FORGE: r = probeForge(m); break;
            default: r = probeVault(m); break;
        }
        if (r != NONE) return r;
        // the wallet's SELL ALL readout works from every page
        if (m.x >= WALLET_SELL_X && m.x <= WALLET_SELL_X + WALLET_SELL_W
                && m.y >= WALLET_Y && m.y <= WALLET_Y + WALLET_H) return SELL;
        return NONE;
    }

    private int probeBay(Vector2 m) {
        for (int c = 0; c < 3; c++) {
            float x0 = bayX(c);
            if (m.x >= x0 && m.x <= x0 + BAY_CW && m.y >= BODY_TOP - BAY_CH && m.y <= BODY_TOP) {
                probeRow = c == 0 ? TAB_FLEET : c == 1 ? TAB_PATTERNS : TAB_FORGE;
                probeSub = c;
                if (lastWorld != null) {
                    if (c == 0) jShip = lastWorld.hangar.shipIndex;
                    if (c == 1) jWeapon = lastWorld.hangar.weaponIndex;
                }
                return TAB;
            }
        }
        for (int r = 0; r < nextCount; r++)
            if (m.x >= BODY_X && m.x <= BODY_X + BODY_W && Math.abs(m.y - nextRowY(r)) <= NEXT_PITCH / 2f) {
                Next n = next[r];
                probeRow = n.dest;
                probeSub = 10 + r;
                jShip = n.jShip;
                jTier = n.jTier;
                jWeapon = n.jWeapon;
                return TAB;
            }
        return NONE;
    }

    private int probeFleet(Vector2 m) {
        int n = ShipDef.ALL.length;
        for (int i = 0; i < n; i++) {
            float x0 = fleetX(i), y1 = fleetTop(i);
            if (m.x >= x0 && m.x <= x0 + FLEET_CW && m.y >= y1 - FLEET_CH && m.y <= y1) {
                probeRow = i;
                return HULL;
            }
        }
        return NONE;
    }

    private int probeRefit(Vector2 m) {
        int n = ShipDef.ALL.length;
        for (int i = 0; i < n; i++) {
            if (Math.abs(m.y - refitRowY(i)) > REFIT_PITCH / 2f) continue;
            if (m.x >= BODY_X && m.x < refitColX(0)) { probeRow = i; return HULL; }
            for (int t = 0; t < 3; t++)
                if (m.x >= refitColX(t) && m.x < refitColX(t) + REFIT_COL_W) {
                    probeRow = t;
                    probeSub = i;
                    jShip = i;
                    return TIER;
                }
        }
        return NONE;
    }

    private int probePatterns(Vector2 m) {
        int n = WeaponDef.ALL.length;
        float p = patPitch(n);
        if (m.x < BODY_X || m.x > BODY_X + PAT_TABLE_W) return NONE;
        for (int i = 0; i < n; i++)
            if (Math.abs(m.y - patRowY(i, n)) <= p / 2f) { probeRow = i; return WEAPON; }
        return NONE;
    }

    private int probeForge(Vector2 m) {
        int n = LokaTable.COUNT, l = curLoka;
        if (l < 0 || l >= n) return NONE;
        float x0 = forgeX(l, n);
        if (m.x >= x0 && m.x <= x0 + forgeCardW(n) && m.y >= BODY_TOP - FORGE_CH && m.y <= BODY_TOP) {
            probeRow = 0;
            return ASTRA;
        }
        return NONE;
    }

    private int probeVault(Vector2 m) {
        float by = vaultBtnY(vaultRows());
        if (Math.abs(m.x - BODY_CX) <= VAULT_BTN_W / 2f && Math.abs(m.y - by) <= VAULT_BTN_H / 2f) {
            probeRow = 0;
            return SELL;
        }
        return NONE;
    }

    // =============================================================== labels (pure — the headless test reads these)
    /** What a tier buys, in the player's words. */
    public static String tierEffect(ShipDef d, int tier) {
        ShipDef.Upgrade u = d.upgrades[tier];
        StringBuilder s = new StringBuilder();
        if (u.damageMult != 1f) add(s, "dmg x" + trim(u.damageMult));
        if (u.cooldownMult != 1f) add(s, "CD x" + trim(u.cooldownMult));
        if (u.dashCdMult != 1f) add(s, "dash x" + trim(u.dashCdMult));
        // "up N", not "+N": the HUD face has no plus glyph
        if (u.hullBonus != 0) add(s, "hull up " + (int) u.hullBonus);
        if (u.shieldBonus != 0) add(s, "shield up " + (int) u.shieldBonus);
        if (u.speedBonus != 0) add(s, "speed up " + (int) u.speedBonus);
        if (u.turnBonus != 0) add(s, "turn up " + (int) u.turnBonus);
        return s.length() == 0 ? "the hull itself" : s.toString();
    }

    /** The selected hull's state line: state, or price plus what to press. */
    public static String hullBuyLine(Hangar h, ShipDef d, int loka) {
        if (h.shipIndex == d.id) return "FLYING";
        if (h.ownsShip(d.id)) return "OWNED";
        if (!LokaTable.shipUnlocked(loka, d.id))
            return "OPENS " + LokaTable.at(d.minLoka).name.toUpperCase();
        return h.coins >= d.cost ? d.cost + "c  [B] BUY" : d.cost + "c  SHORT";
    }

    public static String tierPrice(ShipDef d, int t) {
        return t == 2 ? d.upgrades[t].cost + "c + " + Hangar.SHARD_COST + "sh"
                : d.upgrades[t].cost + "c";
    }

    public static boolean tierAffordable(Hangar h, ShipDef d, int t) {
        return h.coins >= d.upgrades[t].cost
                && (t != 2 || h.shardCount() >= Hangar.SHARD_COST);
    }

    /** The answer line for the selected upgrade node. */
    public static String tierBuyLine(Hangar h, ShipDef d, int t) {
        if (h.owned[d.id][t]) return "OWNED";
        if (!h.ownsShip(d.id)) return "OWN THE HULL FIRST";
        return tierPrice(d, t) + (tierAffordable(h, d, t) ? "  [B] BUY" : "  SHORT");
    }

    public static String weaponState(Hangar h, WeaponDef w) {
        if (h.weaponIndex == w.idx) return "FITTED";
        if (h.weaponOwned[w.idx]) return "OWNED";
        return w.cost + "c";
    }

    /** The pattern panel's answer line. */
    public static String weaponBuyLine(Hangar h, WeaponDef w) {
        if (h.weaponIndex == w.idx) return "FITTED";
        if (h.weaponOwned[w.idx]) return "OWNED";
        return h.coins >= w.cost ? w.cost + "c  [N] BUY" : w.cost + "c  SHORT";
    }

    /** A pattern's numbers as label/value rows for the panel. */
    public static String[][] statRows(WeaponDef d) {
        ArrayList<String[]> r = new ArrayList<>();
        r.add(new String[] { "DAMAGE", String.valueOf((int) d.damage) });
        r.add(new String[] { "COOLDOWN", d.cooldown + "s" });
        r.add(new String[] { "PROJECTILES", "x" + d.count * d.burst });
        if (d.chargeTime > 0) r.add(new String[] { "CHARGE", d.chargeTime + "s" });
        if (d.beam) r.add(new String[] { "BEAM RANGE", String.valueOf((int) d.range) });
        if (d.aoeRadius > 0) r.add(new String[] { "SPLASH", String.valueOf((int) d.aoeRadius) });
        String t = traits(d);
        if (t.length() > 0) r.add(new String[] { "TRAITS", t });
        return r.toArray(new String[0][]);
    }

    private static String traits(WeaponDef d) {
        StringBuilder t = new StringBuilder();
        if (d.homingTurn > 0) add(t, "HOMING");
        if (d.pierceEnemies) add(t, "PIERCE");
        if (d.pierceWalls) add(t, "WALLBORE");
        if (d.aimAssist) add(t, "AIM ASSIST");
        return t.toString();
    }

    /** The forge's state. `forged` is the permanent flag, not the charge count: spending your
     *  last release must not read as "not forged yet". */
    public static String forgeLabel(Hangar h, int loka) {
        int l = Math.max(0, loka);
        if (h.forgeCharges[l] > 0) return "CHARGES " + h.forgeCharges[l] + "   [F] RELEASE";
        if (h.isForged(l)) return "FORGED   [F] WHEN READY";
        return h.forgeProgress[l] + "/" + Hangar.FORGE_COST + " TO FORGE";
    }

    /** The charges in hand, or the shards still to bank. */
    private static String forgeNext(Hangar h, int loka) {
        if (loka < 0 || loka >= LokaTable.COUNT) return "";
        if (h.forgeCharges[loka] > 0) return h.forgeCharges[loka] + " READY";
        int left = Math.max(1, h.forgeNeed(loka) - h.forgeProgress[loka]);
        return "NEXT " + left + "sh";
    }

    /** One plain sentence on what the Astra does (static: it is the same sentence every run). */
    public static String astraEffect() {
        return "20 cubes, each to its own foe, through rock";
    }

    private static String forgeCardState(Hangar h, int l) {
        if (h.forgeCharges[l] > 0)
            return h.forgeCharges[l] + (h.forgeCharges[l] == 1 ? " CHARGE" : " CHARGES");
        if (h.isForged(l)) return "FORGED";
        return h.forgeProgress[l] + "/" + Math.max(1, h.forgeNeed(l));
    }

    private static float forgeFrac(Hangar h, int l) {
        return Math.max(0f, Math.min(1f, h.forgeProgress[l] / (float) Math.max(1, h.forgeNeed(l))));
    }

    private static boolean validLoka(int loka) { return loka >= 0 && loka < LokaTable.COUNT; }

    /** The loka's own tatva element; AKASHA before the first gate. */
    private static int lokaElement(int loka) {
        return validLoka(loka) ? LokaTable.at(loka).shards[0] : Hangar.AKASHA;
    }

    /** Names of the lokas whose own element is `el`. */
    private static String feeds(int el) {
        StringBuilder s = new StringBuilder();
        for (int l = 0; l < LokaTable.COUNT; l++)
            if (LokaTable.at(l).shards[0] == el) {
                if (s.length() > 0) s.append(", ");
                s.append(LokaTable.at(l).name.toUpperCase());
            }
        return s.length() == 0 ? "\u2014" : s.toString();
    }

    private static int artLevel(Hangar h, int i) { return h.owned[i][2] ? 3 : h.owned[i][1] ? 2 : 1; }

    private static void add(StringBuilder s, String part) {
        if (s.length() > 0) s.append("   ");
        s.append(part);
    }
    private static String trim(float m) { return String.valueOf(Math.round(m * 100f) / 100f); }

    // --- hull numbers ---
    private float statValue(int k, Hangar h, ShipDef d) {
        switch (k) {
            case 0: return d.maxHp + h.hullBonus(d);
            case 1: return d.maxSpeed + h.speedBonus(d);
            default: return d.turnRate + h.turnBonus(d);
        }
    }

    private int statSegs(int k, Hangar h, ShipDef d) {
        float max = k == 0 ? maxHp : k == 1 ? maxSpd : maxTurn;
        return Math.max(1, Math.min(5, Math.round(5f * statValue(k, h, d) / max)));
    }

    private String statStr(int k, Hangar h, ShipDef d) {
        return String.valueOf((int) statValue(k, h, d)) + (k == 2 ? "\u00B0" : "");
    }

    /** "HP 120 > 140" — a stat now, and after the upgrade if it changes. */
    private String statAfter(int k, Hangar h, ShipDef d, ShipDef.Upgrade u, boolean owned) {
        float now = statValue(k, h, d);
        float add = owned ? 0 : k == 0 ? u.hullBonus : k == 1 ? u.speedBonus : u.turnBonus;
        String deg = k == 2 ? "\u00B0" : "";
        String s = STAT_NAMES[k] + " " + (int) now + deg;
        return add != 0 ? s + " > " + (int) (now + add) + deg : s;
    }

    // =============================================================== small drawing helpers
    private void gold(float a) { ui.shapes.setColor(SAF[0], SAF[1], SAF[2], a); }

    private void brass(float a) { ui.shapes.setColor(BRASS[0], BRASS[1], BRASS[2], a); }

    private void tone(float[] c, float a) { ui.shapes.setColor(c[0], c[1], c[2], a); }

    private void hair(float x, float y, float w, float a) { brass(a); ui.shapes.rect(x, y, w, 1); }

    private void vhair(float x, float y, float h, float a) { brass(a); ui.shapes.rect(x, y, 1, h); }

    /** A card's body: a lit indigo plate the hairlines and ticks sit on. */
    private void panel(float x0, float y0, float x1, float y1, float a) {
        tone(PANEL, a);
        ui.shapes.rect(x0, y0, x1 - x0, y1 - y0);
    }

    // Ornament and Template draw in the shared accent; the armoury wears saffron for its own passes
    private float sr, sg, sb;

    private void enter() {
        sr = Template.accentR;
        sg = Template.accentG;
        sb = Template.accentB;
        Template.setAccent(SAF[0], SAF[1], SAF[2]);
    }

    private void leave() { Template.setAccent(sr, sg, sb); }

    private void fill(float x, float y, float w, float h, float a) { gold(a); ui.shapes.rect(x, y, w, h); }

    private float capH() {
        gl.setText(ui.hud, "H");
        return gl.height;
    }

    private float textW(String s) {
        gl.setText(ui.hud, s);
        return gl.width;
    }

    /** Four corner ticks round a box, like a viewfinder — the screen's one ornament. */
    private void ticks(float x0, float y0, float x1, float y1, float len, float alpha) {
        if (alpha > 0.85f) gold(alpha);
        else brass(alpha);
        float w = 1.5f;
        ui.shapes.rectLine(x0, y0, x0 + len, y0, w);
        ui.shapes.rectLine(x0, y0, x0, y0 + len, w);
        ui.shapes.rectLine(x1, y0, x1 - len, y0, w);
        ui.shapes.rectLine(x1, y0, x1, y0 + len, w);
        ui.shapes.rectLine(x0, y1, x0 + len, y1, w);
        ui.shapes.rectLine(x0, y1, x0, y1 - len, w);
        ui.shapes.rectLine(x1, y1, x1 - len, y1, w);
        ui.shapes.rectLine(x1, y1, x1, y1 - len, w);
    }

    private void diamondOutline(float x, float y, float r, float w) {
        ui.shapes.rectLine(x - r, y, x, y + r, w);
        ui.shapes.rectLine(x, y + r, x + r, y, w);
        ui.shapes.rectLine(x + r, y, x, y - r, w);
        ui.shapes.rectLine(x, y - r, x - r, y, w);
    }

    /** Solid = owned, outline = not yet. */
    private void pip(float x, float y, boolean on, float a) {
        if (on) {
            gold(0.95f * a);
            Ornament.diamond(ui, x, y, 4);
        } else {
            gold(0.5f * a);
            diamondOutline(x, y, 4, 1.2f);
        }
    }

    /** Five segments, 11 wide on a 14 pitch. */
    private void segs(float x, float cy, int on) {
        for (int s = 0; s < 5; s++) {
            gold(s < on ? 0.9f : 0.2f);
            ui.shapes.rect(x + s * 14, cy - 3, 11, 5);
        }
    }

    private void bar(float x, float cy, float w, float frac, float a) {
        brass(0.25f);
        ui.shapes.rect(x, cy - 2, w, 4);
        gold(a);
        ui.shapes.rect(x, cy - 2, w * frac, 4);
    }

    // =============================================================== radar
    /** Axis k points up for k = 0 and runs clockwise. */
    private static float axisAngle(int k) {
        return (float) (Math.PI / 2 - k * Math.PI * 2 / ShopStats.AXES);
    }

    private void radarGrid(float cx, float cy, float r) {
        for (int ring = 1; ring <= 3; ring++) {
            brass(ring == 3 ? 0.45f : 0.2f);
            float rr = r * ring / 3f;
            for (int k = 0; k < ShopStats.AXES; k++) {
                int n = (k + 1) % ShopStats.AXES;
                ui.shapes.rectLine(cx + (float) Math.cos(axisAngle(k)) * rr, cy + (float) Math.sin(axisAngle(k)) * rr,
                        cx + (float) Math.cos(axisAngle(n)) * rr, cy + (float) Math.sin(axisAngle(n)) * rr, 1f);
            }
        }
        brass(0.2f);
        for (int k = 0; k < ShopStats.AXES; k++)
            ui.shapes.rectLine(cx, cy, cx + (float) Math.cos(axisAngle(k)) * r,
                    cy + (float) Math.sin(axisAngle(k)) * r, 1f);
    }

    private void radarFill(float cx, float cy, float r, float[] f, float[] c, float a) {
        tone(c, a);
        for (int k = 0; k < ShopStats.AXES; k++) {
            int n = (k + 1) % ShopStats.AXES;
            ui.shapes.triangle(cx, cy,
                    cx + (float) Math.cos(axisAngle(k)) * r * f[k], cy + (float) Math.sin(axisAngle(k)) * r * f[k],
                    cx + (float) Math.cos(axisAngle(n)) * r * f[n], cy + (float) Math.sin(axisAngle(n)) * r * f[n]);
        }
    }

    private void radarLine(float cx, float cy, float r, float[] f, float[] c, float a, float w) {
        tone(c, a);
        for (int k = 0; k < ShopStats.AXES; k++) {
            int n = (k + 1) % ShopStats.AXES;
            ui.shapes.rectLine(cx + (float) Math.cos(axisAngle(k)) * r * f[k], cy + (float) Math.sin(axisAngle(k)) * r * f[k],
                    cx + (float) Math.cos(axisAngle(n)) * r * f[n], cy + (float) Math.sin(axisAngle(n)) * r * f[n], w);
        }
    }

    /** The six axis names round the rim. Right-hand names hang off their spoke, left-hand ones end on it. */
    private void radarLabels(SpriteBatch b, float cx, float cy, float r) {
        for (int k = 0; k < ShopStats.AXES; k++) {
            float c = (float) Math.cos(axisAngle(k)), sn = (float) Math.sin(axisAngle(k));
            float x = cx + c * (r + 9), y = cy + sn * (r + 11);
            int anchor = Math.abs(c) < 0.2f ? 0 : c > 0 ? -1 : 1;
            put(b, ShopStats.AXIS_NAMES[k], x, y, anchor, UiTheme.A_LINE, false);
        }
    }

    /** A small legend swatch in the shape pass: a short line in a tone. */
    private void swatch(float x, float y, float[] c, boolean solid) {
        tone(c, solid ? 0.9f : 0.7f);
        ui.shapes.rect(x, y - 2, 18, solid ? 4 : 2);
    }

    // --- text: every call takes the line's CENTRE y, so rows, icons and shapes share one axis ---
    private void put(SpriteBatch b, String s, float x, float cy, int anchor, float a, boolean gold) {
        float w = textW(s);
        float px = anchor < 0 ? x : anchor == 0 ? x - w / 2f : x - w;
        if (gold) ui.hud.setColor(SAF[0], SAF[1], SAF[2], a);
        else ui.hud.setColor(CREAM[0], CREAM[1], CREAM[2], a);
        ui.hud.draw(b, s, px, cy + cap / 2f);
        ui.hud.setColor(1, 1, 1, 1);
    }

    /** A paragraph wrapped to a width, top line's centre at `topCy`; returns the height used. */
    private float wrapped(SpriteBatch b, String s, float x, float topCy, float width, float[] c) {
        ui.hud.setColor(c[0], c[1], c[2], 1f);
        gl.setText(ui.hud, s, ui.hud.getColor(), width, com.badlogic.gdx.utils.Align.left, true);
        ui.hud.draw(b, gl, x, topCy + cap / 2f);
        ui.hud.setColor(1, 1, 1, 1);
        return gl.height;
    }

    /** Text in an exact tone — the gain / loss / brass lines the charts use. */
    private void tinted(SpriteBatch b, String s, float x, float cy, int anchor, float[] c, float a) {
        float w = textW(s);
        float px = anchor < 0 ? x : anchor == 0 ? x - w / 2f : x - w;
        ui.hud.setColor(c[0], c[1], c[2], a);
        ui.hud.draw(b, s, px, cy + cap / 2f);
        ui.hud.setColor(1, 1, 1, 1);
    }

    private void left(SpriteBatch b, String s, float x, float cy, float a) { put(b, s, x, cy, -1, a, false); }
    private void leftGold(SpriteBatch b, String s, float x, float cy, float a) { put(b, s, x, cy, -1, a, true); }
    private void right(SpriteBatch b, String s, float x, float cy, float a) { put(b, s, x, cy, 1, a, false); }
    private void rightGold(SpriteBatch b, String s, float x, float cy, float a) { put(b, s, x, cy, 1, a, true); }
    private void center(SpriteBatch b, String s, float x, float cy, float a) { put(b, s, x, cy, 0, a, false); }
    private void centerGold(SpriteBatch b, String s, float x, float cy, float a) { put(b, s, x, cy, 0, a, true); }

    /** A label/value line: dim label on the left edge, bright value on the right edge. */
    private void pair(SpriteBatch b, String label, String value, float xl, float xr, float cy) {
        left(b, label, xl, cy, UiTheme.A_HINT);
        right(b, value, xr, cy, 1f);
    }

    // --- prices: coin art + number, optionally "+" shard art + number, then a suffix ---
    private float priceW(int coins, int shards, String suffix) {
        float w = 22 + textW(Integer.toString(coins));
        if (shards > 0) w += textW("  +  ") + 22 + textW(Integer.toString(shards));
        return suffix.isEmpty() ? w : w + textW(suffix);
    }

    private void drawPrice(SpriteBatch b, float x, float cy, int coins, int shards, int el,
                           String suffix, boolean hot, float a) {
        float al = hot ? 1f : a;
        b.setColor(1, 1, 1, al);
        b.draw(ui.icons.coin(), x, cy - 8, 16, 16);
        b.setColor(1, 1, 1, 1);
        x += 22;
        String cs = Integer.toString(coins);
        put(b, cs, x, cy, -1, al, hot);
        x += textW(cs);
        if (shards > 0) {
            put(b, "  +  ", x, cy, -1, al, hot);
            x += textW("  +  ");
            b.setColor(1, 1, 1, al);
            b.draw(ui.icons.shard(el), x, cy - 8, 16, 16);
            b.setColor(1, 1, 1, 1);
            x += 22;
            String ss = Integer.toString(shards);
            put(b, ss, x, cy, -1, al, hot);
            x += textW(ss);
        }
        if (!suffix.isEmpty()) put(b, suffix, x, cy, -1, al, hot);
    }

    private void priceCenter(SpriteBatch b, float cx, float cy, int coins, int shards, int el,
                             String suffix, boolean hot, float a) {
        drawPrice(b, cx - priceW(coins, shards, suffix) / 2f, cy, coins, shards, el, suffix, hot, a);
    }

    private void priceRight(SpriteBatch b, float xr, float cy, int coins, int shards, int el,
                            String suffix, boolean hot, float a) {
        drawPrice(b, xr - priceW(coins, shards, suffix), cy, coins, shards, el, suffix, hot, a);
    }

    /** The page's answer line: gold when pressing its key would do something, dim when not. */
    private void answer(SpriteBatch b, String text, boolean actionable) {
        put(b, text, BODY_CX, ANSWER_Y, 0, actionable ? 1f : 0.7f, actionable);
    }

    private void answerPrice(SpriteBatch b, int coins, int shards, int el, String suffix, boolean hot) {
        priceCenter(b, BODY_CX, ANSWER_Y, coins, shards, el, suffix, hot, 0.7f);
    }

    // =============================================================== NEXT UP rows
    private void refreshNext(World w) {
        Hangar h = w.hangar;
        int loka = w.lokaIndex;
        nextCount = 0;

        ShipDef pick = null; // the cheapest hull still to buy
        for (ShipDef d : ShipDef.ALL)
            if (!h.ownsShip(d.id) && LokaTable.shipUnlocked(loka, d.id) && (pick == null || d.cost < pick.cost))
                pick = d;
        if (pick != null) {
            Next n = next[nextCount++];
            n.kind = "HULL";
            n.what = pick.name;
            n.costText = null;
            n.coins = pick.cost;
            n.shards = 0;
            n.ready = h.coins >= pick.cost;
            n.dest = TAB_FLEET;
            n.jShip = pick.id;
            n.jTier = NONE;
            n.jWeapon = NONE;
        }

        ShipDef fd = ShipDef.ALL[h.shipIndex]; // the next upgrade on the hull in hand
        for (int t = 0; t < 3; t++)
            if (!h.owned[fd.id][t]) {
                ShipDef.Upgrade u = fd.upgrades[t];
                Next n = next[nextCount++];
                n.kind = "REFIT";
                n.what = u.label + "  " + u.name;
                n.costText = null;
                n.coins = u.cost;
                n.shards = t == 2 ? Hangar.SHARD_COST : 0;
                n.ready = tierAffordable(h, fd, t);
                n.dest = TAB_REFIT;
                n.jShip = fd.id;
                n.jTier = t;
                n.jWeapon = NONE;
                break;
            }

        WeaponDef wp = null; // the cheapest pattern still to buy
        for (WeaponDef d : WeaponDef.ALL)
            if (!h.weaponOwned[d.idx] && (wp == null || d.cost < wp.cost)) wp = d;
        if (wp != null) {
            Next n = next[nextCount++];
            n.kind = "PATTERN";
            n.what = wp.name;
            n.costText = null;
            n.coins = wp.cost;
            n.shards = 0;
            n.ready = h.coins >= wp.cost;
            n.dest = TAB_PATTERNS;
            n.jShip = NONE;
            n.jTier = NONE;
            n.jWeapon = wp.idx;
        }

        Next n = next[nextCount++];
        n.kind = "ASTRA";
        n.what = "FORGE";
        n.costText = validLoka(loka) ? forgeNext(h, loka) : "\u2014";
        n.ready = validLoka(loka) && h.forgeCharges[loka] > 0;
        n.dest = TAB_FORGE;
        n.jShip = NONE;
        n.jTier = NONE;
        n.jWeapon = NONE;
    }

    // =============================================================== SHAPE PASS
    /** The opaque backdrop and every line — shape pass, before the batch opens. */
    public void drawBackground(World w) {
        Hangar h = w.hangar;
        lastWorld = w;
        curLoka = w.lokaIndex;
        cap = capH();
        refreshNext(w);
        enter();
        // OPAQUE: the world behind the hangar is not a backdrop. Indigo, lit from above.
        ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H,
                new com.badlogic.gdx.graphics.Color(DEEP[0], DEEP[1], DEEP[2], 1f),
                new com.badlogic.gdx.graphics.Color(DEEP[0], DEEP[1], DEEP[2], 1f),
                new com.badlogic.gdx.graphics.Color(INDIGO[0], INDIGO[1], INDIGO[2], 1f),
                new com.badlogic.gdx.graphics.Color(INDIGO[0], INDIGO[1], INDIGO[2], 1f));
        halo();
        Ornament.frame(ui);
        hair(M0, HEAD_RULE_Y, M1 - M0, 0.30f);
        hair(BODY_X, ANSWER_RULE_Y, BODY_W, 0.22f);
        railShapes();
        switch (tab) {
            case TAB_BAY: bayShapes(h, w); break;
            case TAB_FLEET: fleetShapes(h); break;
            case TAB_REFIT: refitShapes(h); break;
            case TAB_PATTERNS: patternShapes(h); break;
            case TAB_FORGE: forgeShapes(h, w.lokaIndex); break;
            default: vaultShapes(h); break;
        }
        leave();
    }

    /** The vajra's halo: three faint rings behind the page, there for the eye and nothing else. */
    private void halo() {
        float cx = BODY_CX + 120, cy = BODY_Y + BODY_H / 2f;
        for (int i = 0; i < 3; i++) {
            float r = 150 + i * 90;
            brass(0.035f);
            int segs = 72;
            for (int k = 0; k < segs; k++) {
                float a0 = (float) (k * Math.PI * 2 / segs), a1 = (float) ((k + 1) * Math.PI * 2 / segs);
                ui.shapes.rectLine(cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                        cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 1.5f);
            }
        }
    }

    private void railShapes() {
        vhair(DIV_X, BODY_Y - 8, BODY_TOP - BODY_Y + 16, 0.22f);
        float cy = railY(tab);
        gold(1f);
        ui.shapes.rect(DIV_X - 1, cy - 13, 3, 26);   // the bright mark where the rail meets the page
        Ornament.diamond(ui, RAIL_X + 5, cy, 3.5f);
        for (int g = 0; g < GROUP_NAMES.length; g++) {
            float gw = textW(GROUP_NAMES[g]);
            hair(RAIL_X + gw + 10, BODY_TOP - GROUP_DY[g], DIV_X - 18 - RAIL_X - gw - 10, 0.18f);
        }
    }

    // --- BAY ---
    private void bayShapes(Hangar h, World w) {
        for (int c = 0; c < 3; c++) {
            float x0 = bayX(c), y1 = BODY_TOP, y0 = y1 - BAY_CH;
            boolean hot = hoverRegion == TAB && hoverSub == c;
            panel(x0, y0, x0 + BAY_CW, y1, 0.7f);
            ticks(x0, y0, x0 + BAY_CW, y1, 14, hot ? 1f : 0.5f);
            hair(x0 + 16, y1 - 40, BAY_CW - 32, 0.22f);
        }
        // hull card: its three tiers as pips
        float cx0 = bayX(0) + BAY_CW / 2f;
        for (int t = 0; t < 3; t++) pip(cx0 + (t - 1) * 22, BODY_TOP - 248, h.owned[h.shipIndex][t], 1f);
        // astra card: progress toward the next release
        if (validLoka(w.lokaIndex))
            bar(bayX(2) + 40, BODY_TOP - 248, BAY_CW - 80, forgeFrac(h, w.lokaIndex), 0.9f);

        hair(BODY_X, nextHeadY() - 16, BODY_W, 0.35f);
        // the quest: the Vajra's seven bands, one pip each — solid once taken back
        hair(BODY_X, questY() + 32, BODY_W, 0.22f);
        for (int i = 0; i < h.pieces.length; i++)
            pip(BODY_X + BODY_W - 20 - (h.pieces.length - 1 - i) * 24, questY() - 6, h.pieces[i], 1f);
        for (int r = 0; r < nextCount; r++)
            if (hoverRegion == TAB && hoverSub == 10 + r)
                fill(BODY_X, nextRowY(r) - NEXT_PITCH / 2f, BODY_W, NEXT_PITCH, 0.08f);
    }

    // --- FLEET ---
    private void fleetShapes(Hangar h) {
        int n = ShipDef.ALL.length;
        for (int i = 0; i < n; i++) {
            float x0 = fleetX(i), y1 = fleetTop(i), y0 = y1 - FLEET_CH;
            boolean sel = i == ship, hot = hoverRegion == HULL && hoverRow == i;
            panel(x0, y0, x0 + FLEET_CW, y1, sel ? 0.95f : 0.55f);
            ticks(x0, y0, x0 + FLEET_CW, y1, 12, sel ? 1f : hot ? 0.6f : 0.3f);
            for (int t = 0; t < 3; t++)
                pip(x0 + FLEET_CW - 18 - (2 - t) * 12, y1 - 18, h.owned[i][t], h.ownsShip(i) ? 1f : 0.35f);
        }
        ShipDef d = ShipDef.ALL[ship];
        hair(BODY_X, fleetBottom(n) - 16, BODY_W, 0.35f);
        float rx = fleetRadarX(), ry = fleetRadarY(n);
        radarGrid(rx, ry, RADAR_R);
        radarLine(rx, ry, RADAR_R, ShopStats.fractions(d, true, true), BRASS, 0.7f, 1f);
        if (ship != h.shipIndex)
            radarLine(rx, ry, RADAR_R, ShopStats.fractions(h, ShipDef.ALL[h.shipIndex]), LAVENDER, 0.95f, 1.5f);
        float[] mine = ShopStats.fractions(h, d);
        radarFill(rx, ry, RADAR_R, mine, SAF, 0.32f);
        radarLine(rx, ry, RADAR_R, mine, SAF, 1f, 2f);
        // the axis table's bars
        float tx = fleetTableX();
        for (int k = 0; k < ShopStats.AXES; k++) {
            float cy = fleetStatY(n, k);
            brass(0.25f);
            ui.shapes.rect(tx + 76, cy - 2, 96, 4);
            ui.shapes.setColor(SAF[0], SAF[1], SAF[2], 0.95f);
            ui.shapes.rect(tx + 76, cy - 2, 96 * mine[k], 4);
            float full = ShopStats.fractions(d, true, true)[k];
            brass(0.9f);
            ui.shapes.rect(tx + 76 + 96 * full - 1, cy - 5, 2, 10); // the fully upgraded mark
        }
        float ly = fleetStatY(n, ShopStats.AXES - 1) - 24;
        swatch(tx, ly, SAF, true);
        swatch(tx + 106, ly, BRASS, false);
        if (ship != h.shipIndex) swatch(tx + 232, ly, LAVENDER, false);
        vhair(fleetUpgX() - 24, fleetStatY(n, 5) - 8, fleetHeadY(n) - fleetStatY(n, 5) + 20, 0.22f);
    }

    // --- REFIT ---
    private void refitShapes(Hangar h) {
        int n = ShipDef.ALL.length;
        hair(BODY_X, BODY_TOP - 34, BODY_W, 0.35f);
        for (int i = 0; i < n; i++) {
            float cy = refitRowY(i);
            if (i == ship) fill(BODY_X, cy - REFIT_PITCH / 2f, BODY_W, REFIT_PITCH, 0.09f);
            else if ((i & 1) == 0) fill(BODY_X, cy - REFIT_PITCH / 2f, BODY_W, REFIT_PITCH, 0.035f);
            if (h.shipIndex == i) {
                gold(1f);
                Ornament.diamond(ui, BODY_X + 10, cy, 3.5f);
            }
            for (int t = 0; t < 3; t++)
                pip(refitColX(t) + 20, cy, h.owned[i][t], h.ownsShip(i) ? 1f : 0.35f);
        }
        if (hoverRegion == TIER && hoverSub >= 0 && !(hoverSub == ship && hoverRow == tier)) {
            float cy = refitRowY(hoverSub), x = refitColX(hoverRow);
            ticks(x + 6, cy - 16, x + REFIT_COL_W - 6, cy + 16, 7, 0.6f);
        }
        float cy = refitRowY(ship), x = refitColX(tier);
        ticks(x + 6, cy - 16, x + REFIT_COL_W - 6, cy + 16, 7, 1f);
        hair(BODY_X, refitBottom(n) - 6, BODY_W, 0.35f);

        // the chart: this hull as it is, and as it would be with the highlighted upgrade
        ShipDef sd = ShipDef.ALL[ship];
        boolean owned = h.owned[ship][tier] || tier == 0;
        float[] before = ShopStats.fractions(h, sd);
        float[] after = ShopStats.fractions(sd, h.owned[ship][1] || tier == 1, h.owned[ship][2] || tier == 2);
        float rx = refitRadarX(), ry = refitRadarY(n);
        radarGrid(rx, ry, REFIT_RADAR_R);
        radarFill(rx, ry, REFIT_RADAR_R, before, SAF, 0.28f);
        if (!owned) {
            radarFill(rx, ry, REFIT_RADAR_R, after, PEACOCK, 0.22f);
            radarLine(rx, ry, REFIT_RADAR_R, after, PEACOCK, 1f, 2f);
        }
        radarLine(rx, ry, REFIT_RADAR_R, before, SAF, 1f, 1.5f);
        vhair(refitTableX() + 300, refitTableY(n, 5) - 10, refitTableY(n, 0) - refitTableY(n, 5) + 30, 0.22f);
    }

    // --- PATTERNS ---
    private void patternShapes(Hangar h) {
        int n = WeaponDef.ALL.length;
        float p = patPitch(n);
        hair(BODY_X, BODY_TOP - 34, PAT_TABLE_W, 0.35f);
        for (int i = 0; i < n; i++) {
            float cy = patRowY(i, n);
            if (i == weapon) {
                fill(BODY_X, cy - p / 2f, PAT_TABLE_W, p, 0.10f);
                fill(BODY_X, cy - p / 2f + 2, 2, p - 4, 1f);
            } else if (hoverRegion == WEAPON && hoverRow == i) fill(BODY_X, cy - p / 2f, PAT_TABLE_W, p, 0.06f);
            else if ((i & 1) == 0) fill(BODY_X, cy - p / 2f, PAT_TABLE_W, p, 0.03f);
        }
        panel(PAT_CARD_X, PAT_CARD_Y0, M1, BODY_TOP, 0.8f);
        ticks(PAT_CARD_X, PAT_CARD_Y0, M1, BODY_TOP, 14, 0.6f);
        hair(PAT_CARD_X + 20, BODY_TOP - 196, PAT_CARD_W - 40, 0.3f);

        WeaponDef sel = WeaponDef.ALL[weapon], fit = WeaponDef.ALL[h.weaponIndex];
        ShopStats.Shot ss = ShopStats.shot(h, sel), fs = ShopStats.shot(h, fit);
        float bx = PAT_CARD_X + 20, bw = PAT_CARD_W - 40;
        for (int m = 0; m < 4; m++) {
            float cy = patChartY(m) - 13;
            brass(0.25f);
            ui.shapes.rect(bx, cy - 3, bw, 6);
            float fr = ShopStats.bar(ss, m);
            boolean ahead = ShopStats.metric(ss, m) >= ShopStats.metric(fs, m) - 0.0001f;
            if (ahead) gold(0.95f);
            else brass(0.9f);
            ui.shapes.rect(bx, cy - 3, bw * fr, 6);
            if (weapon != h.weaponIndex) { // where the pattern in hand sits on the same track
                tone(LAVENDER, 1f);
                ui.shapes.rect(bx + bw * ShopStats.bar(fs, m) - 1, cy - 7, 2, 14);
            }
        }
        // the fan: which way and how wide it throws
        float fx = PAT_CARD_X + 26, fy = PAT_CARD_Y0 + 36, fl = 44;
        brass(0.5f);
        ui.shapes.rect(fx - 3, fy - 3, 6, 6);
        for (int i = 0; i < ss.rays; i++) {
            float off = ss.rays == 1 ? 0 : (float) Math.toRadians(i * ss.spread / (ss.rays - 1) - ss.spread / 2f);
            float ex = fx + (float) Math.cos(off) * fl, ey = fy + (float) Math.sin(off) * fl;
            tone(sel.beam ? PEACOCK : SAF, 0.95f);
            ui.shapes.rectLine(fx, fy, ex, ey, sel.beam ? 3f : 1.5f);
            if (!sel.beam) ui.shapes.circle(ex, ey, 2.5f);
        }
        if (ss.rays > 1) { // the arc the fan spans
            brass(0.4f);
            float half = (float) Math.toRadians(ss.spread / 2f);
            for (int k = 0; k < 12; k++) {
                float a0 = -half + 2 * half * k / 12f, a1 = -half + 2 * half * (k + 1) / 12f;
                ui.shapes.rectLine(fx + (float) Math.cos(a0) * fl, fy + (float) Math.sin(a0) * fl,
                        fx + (float) Math.cos(a1) * fl, fy + (float) Math.sin(a1) * fl, 1f);
            }
        }
        // two seconds of fire, one tick per shot of a held trigger
        float sx0 = PAT_CARD_X + 116, sw = M1 - 20 - sx0, sy = PAT_CARD_Y0 + 36;
        brass(0.3f);
        ui.shapes.rect(sx0, sy - 1, sw, 2);
        float gap = sel.burstGap;
        for (float t = 0; t < 2f; t += ss.cycle)
            for (int pu = 0; pu < ss.pulses; pu++) {
                float tt = t + pu * gap;
                if (tt >= 2f) break;
                gold(0.95f);
                ui.shapes.rect(sx0 + sw * tt / 2f - 1, sy - 8, 2, 16);
            }
    }

    // --- FORGE ---
    private void forgeShapes(Hangar h, int loka) {
        int n = LokaTable.COUNT;
        float cw = forgeCardW(n);
        for (int l = 0; l < n; l++) {
            float x0 = forgeX(l, n), y1 = BODY_TOP, y0 = y1 - FORGE_CH;
            boolean cur = l == loka;
            panel(x0, y0, x0 + cw, y1, cur ? 0.95f : 0.5f);
            ticks(x0, y0, x0 + cw, y1, 12, cur ? 1f : 0.3f);
            hair(x0 + 14, y1 - 42, cw - 28, 0.22f);
            bar(x0 + 20, y1 - 160, cw - 40, forgeFrac(h, l), cur ? 1f : 0.45f);
        }
        float rule = forgeRuleY();
        hair(BODY_X, rule, BODY_W, 0.22f);
        if (validLoka(loka)) {
            int need = Math.max(1, h.forgeNeed(loka)), banked = h.forgeProgress[loka];
            float x0 = BODY_X + 560, x1 = M1 - 8;
            float s = Math.min(26f, (x1 - x0) / need), r = Math.min(8f, s * 0.36f);
            float start = (x0 + x1) / 2f - (need - 1) * s / 2f, y = rule - 74;
            for (int i = 0; i < need; i++) {
                if (i < banked) {
                    gold(0.95f);
                    Ornament.diamond(ui, start + i * s, y, r);
                } else {
                    gold(0.38f);
                    diamondOutline(start + i * s, y, r, 1.3f);
                }
            }
        }
    }

    // --- VAULT ---
    private void vaultShapes(Hangar h) {
        int n = vaultRows();
        hair(BODY_X, BODY_TOP - 34, BODY_W, 0.35f);
        int cur = lokaElement(curLoka);
        for (int i = 0; i < n; i++) {
            float cy = vaultRowY(i);
            if (i == cur) fill(BODY_X, cy - VAULT_PITCH / 2f, BODY_W, VAULT_PITCH, 0.07f);
            else if ((i & 1) == 0) fill(BODY_X, cy - VAULT_PITCH / 2f, BODY_W, VAULT_PITCH, 0.03f);
        }
        hair(BODY_X, vaultRuleY(n), BODY_W, 0.35f);
        boolean can = h.shardCount() > 0, hot = hoverRegion == SELL;
        float by = vaultBtnY(n);
        ticks(BODY_CX - VAULT_BTN_W / 2f, by - VAULT_BTN_H / 2f, BODY_CX + VAULT_BTN_W / 2f,
                by + VAULT_BTN_H / 2f, 10, can ? (hot ? 1f : 0.7f) : 0.26f);
    }

    // =============================================================== BATCH PASS
    /** Art and words — batch pass. */
    public void draw(SpriteBatch batch, World w, ShipRenderer ships, WeaponCards cards) {
        enter();
        Hangar h = w.hangar;
        int loka = w.lokaIndex, el = lokaElement(loka);
        cap = capH();
        drawHeader(batch, h);
        railText(batch, h, loka);
        switch (tab) {
            case TAB_BAY: bayText(batch, w, h, ships, cards, el); break;
            case TAB_FLEET: fleetText(batch, h, loka, ships); break;
            case TAB_REFIT: refitText(batch, h, loka, el); break;
            case TAB_PATTERNS: patternText(batch, h, cards); break;
            case TAB_FORGE: forgeText(batch, h, loka, el); break;
            default: vaultText(batch, h); break;
        }
        footerKeys(batch);
        drawConfirm(batch, h, el);
        leave();
    }

    private void drawHeader(SpriteBatch b, Hangar h) {
        float hy = HEAD_Y + 5 - cap / 2f; // text top rides HEAD_Y + 5, as the wallet always has
        left(b, "ARMOURY OF ATALA", M0, hy, 1f);
        leftGold(b, "/  " + TAB_NAMES[tab], M0 + textW("ARMOURY OF ATALA") + 14, hy, 1f);
        tinted(b, "FORGED IN MAYA'S FIRES  /  RECLAIMED FROM BALA", CX + 40, hy, 0, BRASS, 0.8f);

        int el = lokaElement(curLoka);
        b.draw(ui.icons.coin(), WALLET_X1 - WALLET_COINS_W, hy + cap / 2f - 13, 18, 18);
        right(b, Integer.toString(h.coins), WALLET_X1, hy, 1f);
        float sx = WALLET_X1 - WALLET_COINS_W - WALLET_GAP;
        b.draw(ui.icons.shard(el), sx - WALLET_SHARDS_W, hy + cap / 2f - 13, 18, 18);
        right(b, Integer.toString(h.shardCount()), sx, hy, 1f);
        float a = h.shardCount() == 0 ? 0.4f : 1f; // nothing to sell: dim, but it stays put
        boolean hot = hoverRegion == SELL && h.shardCount() > 0;
        put(b, "SELL", WALLET_SELL_X, hy, -1, a * 0.8f, hot);
        put(b, h.shardCount() + "sh = " + (h.shardCount() * Hangar.SHARD_SELL) + "c",
                WALLET_SELL_X + WALLET_SELL_W, hy, 1, a, hot);
    }

    private void railText(SpriteBatch b, Hangar h, int loka) {
        for (int g = 0; g < GROUP_NAMES.length; g++)
            left(b, GROUP_NAMES[g], RAIL_X, BODY_TOP - GROUP_DY[g], UiTheme.A_HINT);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            float cy = railY(i);
            boolean on = i == tab, hot = hoverRegion == TAB && hoverRow == i && probeSub < 0;
            if (on) leftGold(b, TAB_NAMES[i], RAIL_X + 18, cy, 1f);
            else left(b, TAB_NAMES[i], RAIL_X + 18, cy, hot ? 1f : UiTheme.A_LINE);
            String c = railCount(i, h, loka);
            if (!c.isEmpty()) right(b, c, DIV_X - 16, cy, on ? UiTheme.A_LINE : UiTheme.A_HINT);
        }
    }

    private static String railCount(int t, Hangar h, int loka) {
        int n = 0;
        switch (t) {
            case TAB_FLEET:
                for (ShipDef d : ShipDef.ALL) if (h.ownsShip(d.id)) n++;
                return n + "/" + ShipDef.ALL.length;
            case TAB_REFIT:
                for (ShipDef d : ShipDef.ALL) for (int k = 0; k < 3; k++) if (h.owned[d.id][k]) n++;
                return n + "/" + ShipDef.ALL.length * 3;
            case TAB_PATTERNS:
                for (WeaponDef d : WeaponDef.ALL) if (h.weaponOwned[d.idx]) n++;
                return n + "/" + WeaponDef.ALL.length;
            case TAB_FORGE:
                return validLoka(loka) && h.forgeCharges[loka] > 0 ? "x" + h.forgeCharges[loka] : "";
            case TAB_VAULT:
                return h.shardCount() + "sh";
            default:
                return "";
        }
    }

    /** The per-section keys line, two-tone like every page. */
    private void footerKeys(SpriteBatch batch) {
        if (confirm != null) { // the page is behind the modal: its keys are not on offer
            Template.keys(ui, batch, FOOTER_Y, "ENTER", "BUY", "ESC", "CANCEL");
            return;
        }
        switch (tab) {
            case TAB_BAY:
                Template.keys(ui, batch, FOOTER_Y, "CLICK", "OPEN", "TAB", "NEXT SECTION",
                        "ENTER/ESC", "CLOSE");
                break;
            case TAB_FLEET:
            case TAB_REFIT:
                Template.keys(ui, batch, FOOTER_Y, "WASD", "MOVE", "B", "BUY", "TAB", "NEXT SECTION",
                        "ENTER/ESC", "CLOSE");
                break;
            case TAB_PATTERNS:
                Template.keys(ui, batch, FOOTER_Y, "W/S", "PATTERN", "N", "BUY", "TAB", "NEXT SECTION",
                        "ENTER/ESC", "CLOSE");
                break;
            case TAB_FORGE:
                Template.keys(ui, batch, FOOTER_Y, "F", "RELEASE", "X", "SELL ALL", "TAB", "NEXT SECTION",
                        "ENTER/ESC", "CLOSE");
                break;
            default:
                Template.keys(ui, batch, FOOTER_Y, "X", "SELL ALL", "TAB", "NEXT SECTION",
                        "ENTER/ESC", "CLOSE");
        }
    }

    // --- BAY ---
    private void bayText(SpriteBatch b, World w, Hangar h, ShipRenderer ships, WeaponCards cards, int el) {
        int loka = w.lokaIndex;
        float top = BODY_TOP;
        ShipDef d = ShipDef.ALL[h.shipIndex];
        WeaponDef wd = WeaponDef.ALL[h.weaponIndex];

        // hull card
        float x0 = bayX(0), cx = x0 + BAY_CW / 2f;
        left(b, "HULL", x0 + 16, top - 22, UiTheme.A_HINT);
        TextureRegion r = ships.hull(h.shipIndex, artLevel(h, h.shipIndex));
        if (r != null) b.draw(r, cx - 56, top - 118 - 56, 112, 112);
        center(b, d.name, cx, top - 192, 1f);
        center(b, "HP " + statStr(0, h, d) + "   SPD " + statStr(1, h, d) + "   TURN " + statStr(2, h, d),
                cx, top - 220, UiTheme.A_LINE);

        // pattern card
        x0 = bayX(1);
        cx = x0 + BAY_CW / 2f;
        left(b, "PATTERN", x0 + 16, top - 22, UiTheme.A_HINT);
        b.draw(cards.get(h.weaponIndex), cx - 44, top - 118 - 44, 88, 88);
        center(b, wd.name, cx, top - 192, 1f);
        center(b, "DPS " + Math.round(ShopStats.shot(h, wd).dps) + "   DMG " + (int) wd.damage
                + "   CD " + wd.cooldown + "s   x" + wd.count * wd.burst, cx, top - 220, UiTheme.A_LINE);
        String t = traits(wd);
        center(b, t.isEmpty() ? "\u2014" : t, cx, top - 248, UiTheme.A_HINT);

        // astra card
        x0 = bayX(2);
        cx = x0 + BAY_CW / 2f;
        left(b, "ASTRA", x0 + 16, top - 22, UiTheme.A_HINT);
        b.draw(ui.icons.shard(el), cx - 32, top - 118 - 32, 64, 64);
        center(b, World.TATVA[el].toUpperCase(), cx, top - 192, 1f);
        if (validLoka(loka) && h.forgeCharges[loka] > 0)
            centerGold(b, forgeNext(h, loka), cx, top - 220, 1f);
        else center(b, validLoka(loka) ? forgeNext(h, loka) : "NO LOKA YET", cx, top - 220, UiTheme.A_LINE);

        // NEXT UP
        float hy = nextHeadY();
        left(b, "NEXT UP", BODY_X + 8, hy, UiTheme.A_HINT);
        left(b, "COST", BODY_X + 560, hy, UiTheme.A_HINT);
        right(b, "STATUS", BODY_X + BODY_W - 8, hy, UiTheme.A_HINT);
        if (nextCount == 0) center(b, "NOTHING LEFT TO BUY", BODY_CX, nextRowY(0), UiTheme.A_HINT);
        for (int i = 0; i < nextCount; i++) {
            Next n = next[i];
            float cy = nextRowY(i);
            boolean hot = hoverRegion == TAB && hoverSub == 10 + i;
            left(b, n.kind, BODY_X + 8, cy, UiTheme.A_HINT);
            left(b, n.what, BODY_X + 150, cy, hot ? 1f : UiTheme.A_LINE);
            if (n.costText != null) left(b, n.costText, BODY_X + 560, cy, UiTheme.A_LINE);
            else drawPrice(b, BODY_X + 560, cy, n.coins, n.shards, el, "", n.ready, UiTheme.A_HINT);
            if (n.ready) rightGold(b, "READY", BODY_X + BODY_W - 8, cy, 1f);
            else if (n.costText == null) right(b, "SHORT", BODY_X + BODY_W - 8, cy, UiTheme.A_HINT);
        }
        // the quest strip
        int bands = 0;
        for (boolean p : h.pieces) if (p) bands++;
        left(b, "THE VAJRA", BODY_X + 8, questY() + 14, UiTheme.A_HINT);
        right(b, bands + " OF 7 BANDS", BODY_X + BODY_W - 8, questY() + 14, UiTheme.A_LINE);
        wrapped(b, "Trivikrama broke the Vajrayudha and hid a band in each loka of Patala. "
                + "Bala, son of Maya, keeps Atala's forges. Take what you need, then climb.",
                BODY_X + 8, questY() - 8, BODY_W - 260, BRASS);
        answer(b, "FLYING " + d.name + "   /   FITTED " + wd.name, false);
    }

    // --- FLEET ---
    private void fleetText(SpriteBatch b, Hangar h, int loka, ShipRenderer ships) {
        int n = ShipDef.ALL.length;
        for (int i = 0; i < n; i++) {
            ShipDef hd = ShipDef.ALL[i];
            float x0 = fleetX(i), y1 = fleetTop(i), cx = x0 + FLEET_CW / 2f;
            boolean sel = i == ship;
            boolean locked = !h.ownsShip(i) && !LokaTable.shipUnlocked(loka, i);
            float a = locked ? 0.6f : 1f;
            left(b, (i < 9 ? "0" : "") + (i + 1), x0 + 14, y1 - 18, UiTheme.A_HINT);
            TextureRegion r = ships.hull(i, artLevel(h, i));
            if (r != null) {
                b.setColor(1, 1, 1, a);
                b.draw(r, cx - 30, y1 - 46 - 30, 60, 60);
                b.setColor(1, 1, 1, 1);
            }
            if (sel) centerGold(b, hd.name, cx, y1 - 88, 1f);
            else center(b, hd.name, cx, y1 - 88, a);
            float sy = y1 - 108;
            if (h.shipIndex == hd.id) centerGold(b, "FLYING", cx, sy, 1f);
            else if (h.ownsShip(i) || locked) center(b, hullBuyLine(h, hd, loka), cx, sy, 0.7f);
            else priceCenter(b, cx, sy, hd.cost, 0, 0, "", h.coins >= hd.cost, 0.7f);
        }

        ShipDef sel = ShipDef.ALL[ship], fly = ShipDef.ALL[h.shipIndex];
        float head = fleetHeadY(n);
        float tx = fleetTableX(), ux = fleetUpgX();
        radarLabels(b, fleetRadarX(), fleetRadarY(n), RADAR_R);
        center(b, sel.flavor.toUpperCase(), fleetRadarX(), fleetRadarY(n) - RADAR_R - 32, UiTheme.A_HINT);
        center(b, ShopStats.role(sel), fleetRadarX(), fleetRadarY(n) - RADAR_R - 50, UiTheme.A_LINE);

        leftGold(b, sel.name, tx, head, 1f);
        right(b, "NOW", tx + 232, head, UiTheme.A_HINT);
        right(b, "VS FLYING", tx + 330, head, UiTheme.A_HINT);
        float[] now = ShopStats.raw(sel, h.owned[ship][1], h.owned[ship][2]);
        float[] flyRaw = ShopStats.raw(fly, h.owned[fly.id][1], h.owned[fly.id][2]);
        for (int k = 0; k < ShopStats.AXES; k++) {
            float cy = fleetStatY(n, k);
            left(b, ShopStats.AXIS_NAMES[k], tx, cy, UiTheme.A_LINE);
            right(b, ShopStats.axisText(k, now[k]), tx + 232, cy, 1f);
            int pct = ShopStats.versusPct(now[k], flyRaw[k]);
            if (sel == fly) continue;
            if (pct == 0) right(b, "same", tx + 330, cy, UiTheme.A_HINT);
            else tinted(b, ShopStats.versus(now[k], flyRaw[k]), tx + 330, cy, 1, pct > 0 ? PEACOCK : ROSE, 1f);
        }
        float ly = fleetStatY(n, ShopStats.AXES - 1) - 24;
        tinted(b, "THIS HULL", tx + 22, ly, -1, CREAM, UiTheme.A_LINE);
        tinted(b, "UPGRADED", tx + 128, ly, -1, BRASS, 1f);
        if (ship != h.shipIndex) tinted(b, "FLYING", tx + 254, ly, -1, LAVENDER, 1f);

        left(b, "UPGRADES", ux, head, UiTheme.A_HINT);
        for (int k = 1; k < 3; k++) {
            ShipDef.Upgrade u = sel.upgrades[k];
            float cy = head - 28 - (k - 1) * 58;
            if (h.owned[ship][k]) tinted(b, u.label + "  " + u.name, ux, cy, -1, CREAM, 1f);
            else left(b, u.label + "  " + u.name, ux, cy, UiTheme.A_LINE);
            tinted(b, tierEffect(sel, k), ux, cy - 20, -1, BRASS, 1f);
            float xr = BODY_X + BODY_W - 8;
            if (h.owned[ship][k]) rightGold(b, "OWNED", xr, cy, 1f);
            else if (!h.ownsShip(ship)) right(b, "\u2014", xr, cy, UiTheme.A_HINT);
            else priceRight(b, xr, cy, u.cost, k == 2 ? Hangar.SHARD_COST : 0, lokaElement(loka), "",
                        tierAffordable(h, sel, k), UiTheme.A_HINT);
        }

        if (h.shipIndex == sel.id) answer(b, "FLYING", false);
        else if (h.ownsShip(sel.id) || !LokaTable.shipUnlocked(loka, sel.id))
            answer(b, hullBuyLine(h, sel, loka), false);
        else {
            boolean aff = h.coins >= sel.cost;
            answerPrice(b, sel.cost, 0, 0, aff ? "  [B] BUY" : "  SHORT", aff);
        }
    }

    // --- REFIT ---
    private void refitText(SpriteBatch b, Hangar h, int loka, int el) {
        int n = ShipDef.ALL.length;
        ShipDef sd = ShipDef.ALL[ship];
        float hy = BODY_TOP - 14;
        left(b, "HULL", BODY_X + 24, hy, UiTheme.A_HINT);
        for (int t = 0; t < 3; t++) {
            String s = sd.upgrades[t].label + "  " + sd.upgrades[t].name;
            if (t == tier) leftGold(b, s, refitColX(t) + 8, hy, 1f);
            else left(b, s, refitColX(t) + 8, hy, UiTheme.A_HINT);
        }
        for (int i = 0; i < n; i++) {
            ShipDef d = ShipDef.ALL[i];
            float cy = refitRowY(i);
            boolean ownedHull = h.ownsShip(i);
            boolean locked = !ownedHull && !LokaTable.shipUnlocked(loka, i);
            float a = ownedHull ? 1f : locked ? 0.35f : 0.6f;
            if (i == ship) leftGold(b, d.name, BODY_X + 24, cy, 1f);
            else left(b, d.name, BODY_X + 24, cy, a);
            for (int t = 0; t < 3; t++) {
                float xr = refitColX(t) + REFIT_COL_W - 14;
                if (h.owned[i][t]) right(b, "OWNED", xr, cy, 0.7f);
                else if (!ownedHull) right(b, "\u2014", xr, cy, UiTheme.A_HINT);
                else priceRight(b, xr, cy, d.upgrades[t].cost, t == 2 ? Hangar.SHARD_COST : 0, el, "",
                            tierAffordable(h, d, t), UiTheme.A_HINT);
            }
        }

        ShipDef.Upgrade u = sd.upgrades[tier];
        boolean owned = h.owned[ship][tier];
        float bottom = refitBottom(n);
        radarLabels(b, refitRadarX(), refitRadarY(n), REFIT_RADAR_R);
        float ty0 = bottom - 14;
        leftGold(b, u.label + "   " + u.name, BODY_X + 8, ty0, 1f);
        tinted(b, tier == 0 ? "the hull itself" : tierEffect(sd, tier),
                BODY_X + 24 + textW(u.label + "   " + u.name), ty0, -1, BRASS, 1f);

        // before -> after, one row per axis: the part of an upgrade people actually want to know
        float tx = refitTableX();
        float hy2 = bottom - 34;
        left(b, "BEFORE", tx + 80, hy2, UiTheme.A_HINT);
        left(b, "AFTER", tx + 172, hy2, UiTheme.A_HINT);
        right(b, "CHANGE", tx + 292, hy2, UiTheme.A_HINT);
        float[] was = ShopStats.raw(sd, h.owned[ship][1], h.owned[ship][2]);
        float[] will = ShopStats.raw(sd, h.owned[ship][1] || tier == 1, h.owned[ship][2] || tier == 2);
        for (int k = 0; k < ShopStats.AXES; k++) {
            float cy = refitTableY(n, k);
            left(b, ShopStats.AXIS_NAMES[k], tx, cy, UiTheme.A_LINE);
            left(b, ShopStats.axisText(k, was[k]), tx + 80, cy, 1f);
            int pct = ShopStats.versusPct(will[k], was[k]);
            if (owned || pct == 0) {
                left(b, owned ? "owned" : "same", tx + 172, cy, UiTheme.A_HINT);
            } else {
                tinted(b, ShopStats.axisText(k, will[k]), tx + 172, cy, -1, PEACOCK, 1f);
                tinted(b, ShopStats.versus(will[k], was[k]), tx + 292, cy, 1, pct > 0 ? PEACOCK : ROSE, 1f);
            }
        }
        float hx = tx + 322;
        left(b, "WHAT THE AXES MEAN", hx, hy2, UiTheme.A_HINT);
        for (int k = 0; k < ShopStats.AXES; k++) {
            float cy = refitTableY(n, k);
            tinted(b, ShopStats.AXIS_NAMES[k], hx, cy, -1, CREAM, 0.9f);
            tinted(b, ShopStats.AXIS_HELP[k], hx + 70, cy, -1, BRASS, 1f);
        }

        if (owned) answer(b, "OWNED", false);
        else if (!h.ownsShip(sd.id)) answer(b, "OWN THE HULL FIRST", false);
        else {
            boolean aff = tierAffordable(h, sd, tier);
            answerPrice(b, u.cost, tier == 2 ? Hangar.SHARD_COST : 0, el, aff ? "  [B] BUY" : "  SHORT", aff);
        }
    }

    // --- PATTERNS ---
    private void patternText(SpriteBatch b, Hangar h, WeaponCards cards) {
        int n = WeaponDef.ALL.length;
        float hy = BODY_TOP - 14;
        right(b, "#", BODY_X + 28, hy, UiTheme.A_HINT);
        left(b, "PATTERN", BODY_X + 44, hy, UiTheme.A_HINT);
        right(b, "DMG", BODY_X + 300, hy, UiTheme.A_HINT);
        right(b, "CD", BODY_X + 376, hy, UiTheme.A_HINT);
        right(b, "SHOTS", BODY_X + 442, hy, UiTheme.A_HINT);
        right(b, "DPS", BODY_X + 524, hy, UiTheme.A_HINT);
        right(b, "STATE", BODY_X + PAT_TABLE_W - 8, hy, UiTheme.A_HINT);
        for (int i = 0; i < n; i++) {
            WeaponDef d = WeaponDef.ALL[i];
            float cy = patRowY(i, n);
            boolean owned = h.weaponOwned[i], afford = h.coins >= d.cost;
            right(b, Integer.toString(i + 1), BODY_X + 28, cy, UiTheme.A_HINT);
            if (i == weapon) leftGold(b, d.name, BODY_X + 44, cy, 1f);
            else left(b, d.name, BODY_X + 44, cy, owned ? 1f : afford ? 0.8f : 0.55f);
            right(b, Integer.toString((int) d.damage), BODY_X + 300, cy, UiTheme.A_LINE);
            right(b, d.cooldown + "s", BODY_X + 376, cy, UiTheme.A_LINE);
            right(b, "x" + d.count * d.burst, BODY_X + 442, cy, UiTheme.A_LINE);
            right(b, Integer.toString(Math.round(ShopStats.shot(h, d).dps)), BODY_X + 524, cy, 1f);
            float xr = BODY_X + PAT_TABLE_W - 8;
            if (h.weaponIndex == i) rightGold(b, "FITTED", xr, cy, 1f);
            else if (owned) right(b, "OWNED", xr, cy, 0.7f);
            else priceRight(b, xr, cy, d.cost, 0, 0, "", afford, UiTheme.A_HINT);
        }

        // the detail card
        WeaponDef sel = WeaponDef.ALL[weapon], fit = WeaponDef.ALL[h.weaponIndex];
        boolean owned = h.weaponOwned[weapon];
        float cx = PAT_CARD_X + PAT_CARD_W / 2f;
        b.setColor(1, 1, 1, owned ? 1f : 0.6f);
        b.draw(cards.get(weapon), cx - 36, BODY_TOP - 52 - 36, 72, 72);
        b.setColor(1, 1, 1, 1);
        center(b, sel.name, cx, BODY_TOP - 104, 1f);
        float sy = BODY_TOP - 124;
        if (h.weaponIndex == weapon) centerGold(b, weaponState(h, sel), cx, sy, 1f);
        else if (owned) center(b, weaponState(h, sel), cx, sy, 0.7f);
        else priceCenter(b, cx, sy, sel.cost, 0, 0, "", h.coins >= sel.cost, UiTheme.A_HINT);
        wrapped(b, ShopStats.howItPlays(sel), PAT_CARD_X + 20, BODY_TOP - 142, PAT_CARD_W - 40, BRASS);

        ShopStats.Shot ss = ShopStats.shot(h, sel), fs = ShopStats.shot(h, fit);
        float xl = PAT_CARD_X + 20, xr = M1 - 20;
        for (int m = 0; m < 4; m++) {
            float cy = patChartY(m);
            left(b, ShopStats.METRIC_NAMES[m], xl, cy, UiTheme.A_LINE);
            right(b, ShopStats.metricText(ss, m), xr, cy, 1f);
        }
        float vy = BODY_TOP - 210;
        if (weapon == h.weaponIndex) {
            tinted(b, "THE ONE IN YOUR HANDS", xl, vy, -1, LAVENDER, 1f);
        } else {
            int dp = ShopStats.versusPct(ss.dps, fs.dps), rp = ShopStats.versusPct(ss.reach, fs.reach);
            String reach = "REACH " + ShopStats.versus(ss.reach, fs.reach);
            left(b, "VS FITTED", xl, vy, UiTheme.A_HINT);
            tinted(b, "DPS " + ShopStats.versus(ss.dps, fs.dps), xr - textW(reach) - 14,
                    vy, 1, dp >= 0 ? PEACOCK : ROSE, 1f);
            tinted(b, reach, xr, vy, 1, rp >= 0 ? PEACOCK : ROSE, 1f);
        }
        float fy = PAT_CARD_Y0 + 36;
        left(b, ss.rays > 1 ? Math.round(ss.spread) + " deg FAN" : sel.beam ? "BEAM" : "STRAIGHT",
                PAT_CARD_X + 20, fy - 26, UiTheme.A_HINT);
        left(b, "2 SECONDS OF FIRE", PAT_CARD_X + 116, fy - 26, UiTheme.A_HINT);
        left(b, "NUMBERS WITH YOUR HULL", xl, patChartY(3) - 30, UiTheme.A_HINT);

        if (owned) answer(b, weaponBuyLine(h, sel), false);
        else {
            boolean aff = h.coins >= sel.cost;
            answerPrice(b, sel.cost, 0, 0, aff ? "  [N] BUY" : "  SHORT", aff);
        }
    }

    // --- FORGE ---
    private void forgeText(SpriteBatch b, Hangar h, int loka, int el) {
        int n = LokaTable.COUNT;
        float cw = forgeCardW(n);
        for (int l = 0; l < n; l++) {
            float x0 = forgeX(l, n), y1 = BODY_TOP, cx = x0 + cw / 2f;
            boolean cur = l == loka;
            float a = cur ? 1f : 0.55f;
            int lel = LokaTable.at(l).shards[0];
            String name = LokaTable.at(l).name.toUpperCase();
            if (cur) centerGold(b, name, cx, y1 - 22, 1f);
            else center(b, name, cx, y1 - 22, a);
            b.setColor(1, 1, 1, a);
            b.draw(ui.icons.shard(lel), cx - 22, y1 - 92 - 22, 44, 44);
            b.setColor(1, 1, 1, 1);
            center(b, World.TATVA[lel].toUpperCase(), cx, y1 - 136, UiTheme.A_HINT);
            if (cur && h.forgeCharges[l] > 0) centerGold(b, forgeCardState(h, l), cx, y1 - 186, 1f);
            else center(b, forgeCardState(h, l), cx, y1 - 186, a);
        }

        float rule = forgeRuleY();
        left(b, "THE ASTRA", BODY_X + 8, rule - 22, UiTheme.A_HINT);
        left(b, "NEXT RELEASE", BODY_X + 560, rule - 22, UiTheme.A_HINT);
        boolean ok = validLoka(loka);
        float xl = BODY_X + 8, xr = BODY_X + 500;
        pair(b, "EFFECT", astraEffect(), xl, xr, rule - 52);
        pair(b, "HELD", ok ? h.shards[el] + " " + World.TATVA[el].toLowerCase() : "\u2014", xl, xr, rule - 78);
        pair(b, "BANKED", ok ? h.forgeProgress[loka] + "/" + Math.max(1, h.forgeNeed(loka)) : "\u2014", xl, xr, rule - 104);
        pair(b, "CHARGES", ok ? Integer.toString(h.forgeCharges[loka]) : "\u2014", xl, xr, rule - 130);
        if (ok) center(b, forgeNext(h, loka), (BODY_X + 560 + M1 - 8) / 2f, rule - 110, UiTheme.A_LINE);

        answer(b, forgeLabel(h, loka), ok && h.forgeCharges[loka] > 0);
    }

    // --- VAULT ---
    private void vaultText(SpriteBatch b, Hangar h) {
        int n = vaultRows();
        float hy = BODY_TOP - 14;
        left(b, "ELEMENT", BODY_X + 12, hy, UiTheme.A_HINT);
        right(b, "HELD", BODY_X + 360, hy, UiTheme.A_HINT);
        right(b, "WORTH", BODY_X + 500, hy, UiTheme.A_HINT);
        left(b, "FEEDS", BODY_X + 560, hy, UiTheme.A_HINT);
        int cur = lokaElement(curLoka), total = 0;
        for (int i = 0; i < n; i++) {
            float cy = vaultRowY(i);
            int held = i < h.shards.length ? h.shards[i] : 0;
            total += held;
            b.draw(ui.icons.shard(i), BODY_X + 12, cy - 11, 22, 22);
            if (i == cur) leftGold(b, World.TATVA[i].toUpperCase(), BODY_X + 44, cy, 1f);
            else left(b, World.TATVA[i].toUpperCase(), BODY_X + 44, cy, UiTheme.A_LINE);
            right(b, Integer.toString(held), BODY_X + 360, cy, held > 0 ? 1f : UiTheme.A_HINT);
            priceRight(b, BODY_X + 500, cy, held * Hangar.SHARD_SELL, 0, 0, "", held > 0, UiTheme.A_HINT);
            left(b, feeds(i), BODY_X + 560, cy, UiTheme.A_HINT);
        }
        float ty = vaultTotalY(n);
        left(b, "TOTAL", BODY_X + 12, ty, UiTheme.A_HINT);
        right(b, Integer.toString(total), BODY_X + 360, ty, 1f);
        priceRight(b, BODY_X + 500, ty, total * Hangar.SHARD_SELL, 0, 0, "", total > 0, UiTheme.A_HINT);

        boolean can = h.shardCount() > 0;
        float by = vaultBtnY(n);
        if (can) centerGold(b, "SELL ALL", BODY_CX, by, 1f);
        else center(b, "SELL ALL", BODY_CX, by, UiTheme.A_HINT);

        if (can) answerPrice(b, h.shardCount() * Hangar.SHARD_SELL, 0, 0, "  [X] SELL ALL", true);
        else answer(b, "NO SHARDS TO SELL", false);
    }
}