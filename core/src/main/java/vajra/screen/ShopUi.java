package vajra.screen;

import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;
import vajra.world.LokaTable;
import vajra.world.World;

import java.util.ArrayList;

/** THE HANGAR — one opaque screen: a rail of sections on the left, one page on the right.
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
 *  <p>LOOK — gold hairlines on ink. Gold means "selected" or "pressing the key would do
 *  something"; white is bright or dim. The only ornament is four corner ticks on a card. Coin and
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
    public static final String[] TAB_NAMES = { "BAY", "FLEET", "REFIT", "PATTERNS", "FORGE", "VAULT" };

    // clickable regions
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
    private static final float NEXT_PITCH = 28;

    // --- FLEET ---
    private static final int FLEET_COLS = 4;
    private static final float FLEET_CW = 226, FLEET_CH = 156, FLEET_RGAP = 18;
    private static final float FLEET_GAP = (BODY_W - FLEET_COLS * FLEET_CW) / (FLEET_COLS - 1);

    // --- REFIT ---
    private static final float REFIT_NAME_W = 214, REFIT_PITCH = 40;
    private static final float REFIT_COL_W = (BODY_W - REFIT_NAME_W) / 3f;

    // --- PATTERNS ---
    private static final float PAT_TABLE_W = 628;
    private static final float PAT_CARD_X = BODY_X + PAT_TABLE_W + 36;
    private static final float PAT_CARD_W = M1 - PAT_CARD_X;
    private static final float PAT_CARD_Y0 = 176;

    // --- FORGE ---
    private static final float FORGE_CH = 232, FORGE_GAP = 14, FORGE_MAXW = 190;

    // --- VAULT ---
    private static final float VAULT_PITCH = 40, VAULT_BTN_W = 420, VAULT_BTN_H = 44;

    private static final String[] STAT_NAMES = { "HP", "SPEED", "TURN" };

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
    private static float nextRowY(int r) { return nextHeadY() - 16 - 20 - r * NEXT_PITCH; }

    private static float fleetX(int i) { return BODY_X + (i % FLEET_COLS) * (FLEET_CW + FLEET_GAP); }
    private static float fleetTop(int i) { return BODY_TOP - (i / FLEET_COLS) * (FLEET_CH + FLEET_RGAP); }
    private static float fleetBottom(int n) {
        int rows = (n + FLEET_COLS - 1) / FLEET_COLS;
        return BODY_TOP - (rows - 1) * (FLEET_CH + FLEET_RGAP) - FLEET_CH;
    }
    private static float fleetHeadY(int n) { return fleetBottom(n) - 36; }
    private static float fleetStatY(int n, int k) { return fleetHeadY(n) - 28 - k * 26; }

    private static float refitColX(int t) { return BODY_X + REFIT_NAME_W + t * REFIT_COL_W; }
    private static float refitRowY(int i) { return BODY_TOP - 54 - i * REFIT_PITCH; }
    private static float refitBottom(int n) { return refitRowY(n - 1) - REFIT_PITCH / 2f; }

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
        if (u.hullBonus != 0) add(s, "hull +" + (int) u.hullBonus);
        if (u.shieldBonus != 0) add(s, "shield +" + (int) u.shieldBonus);
        if (u.speedBonus != 0) add(s, "spd +" + (int) u.speedBonus);
        if (u.turnBonus != 0) add(s, "turn +" + (int) u.turnBonus);
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
        return s.length() == 0 ? "-" : s.toString();
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
    private void gold(float a) {
        ui.shapes.setColor(Template.accentR, Template.accentG, Template.accentB, a);
    }

    private void hair(float x, float y, float w, float a) { gold(a); ui.shapes.rect(x, y, w, 1); }

    private void vhair(float x, float y, float h, float a) { gold(a); ui.shapes.rect(x, y, 1, h); }

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
        gold(alpha);
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
        gold(0.2f);
        ui.shapes.rect(x, cy - 2, w, 4);
        gold(a);
        ui.shapes.rect(x, cy - 2, w * frac, 4);
    }

    // --- text: every call takes the line's CENTRE y, so rows, icons and shapes share one axis ---
    private void put(SpriteBatch b, String s, float x, float cy, int anchor, float a, boolean gold) {
        float w = textW(s);
        float px = anchor < 0 ? x : anchor == 0 ? x - w / 2f : x - w;
        if (gold) ui.hud.setColor(Template.accentR, Template.accentG, Template.accentB, a);
        else ui.hud.setColor(1, 1, 1, a);
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
        n.costText = validLoka(loka) ? forgeNext(h, loka) : "-";
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
        Template.dim(ui, 1f); // OPAQUE: the world behind the hangar is not a backdrop
        Ornament.frame(ui);
        hair(M0, HEAD_RULE_Y, M1 - M0, 0.22f);
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
            ticks(x0, y0, x0 + FLEET_CW, y1, 12, sel ? 1f : hot ? 0.6f : 0.26f);
            for (int t = 0; t < 3; t++)
                pip(x0 + FLEET_CW - 18 - (2 - t) * 12, y1 - 18, h.owned[i][t], h.ownsShip(i) ? 1f : 0.35f);
        }
        ShipDef d = ShipDef.ALL[ship];
        float head = fleetHeadY(n);
        hair(BODY_X, fleetBottom(n) - 16, BODY_W, 0.35f);
        vhair(BODY_X + 480, fleetStatY(n, 2) - 14, head - fleetStatY(n, 2) + 28, 0.22f);
        for (int k = 0; k < 3; k++) segs(BODY_X + 110, fleetStatY(n, k), statSegs(k, h, d));
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
        hair(BODY_X, refitBottom(n) - 6, BODY_W, 0.22f);
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
        ticks(PAT_CARD_X, PAT_CARD_Y0, M1, BODY_TOP, 14, 0.6f);
        hair(PAT_CARD_X + 20, BODY_TOP - 196, PAT_CARD_W - 40, 0.3f);
    }

    // --- FORGE ---
    private void forgeShapes(Hangar h, int loka) {
        int n = LokaTable.COUNT;
        float cw = forgeCardW(n);
        for (int l = 0; l < n; l++) {
            float x0 = forgeX(l, n), y1 = BODY_TOP, y0 = y1 - FORGE_CH;
            boolean cur = l == loka;
            ticks(x0, y0, x0 + cw, y1, 12, cur ? 1f : 0.26f);
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
    }

    private void drawHeader(SpriteBatch b, Hangar h) {
        float hy = HEAD_Y + 5 - cap / 2f; // text top rides HEAD_Y + 5, as the wallet always has
        left(b, "HANGAR", M0, hy, 1f);
        leftGold(b, "\u00B7  " + TAB_NAMES[tab], M0 + textW("HANGAR") + 14, hy, 1f);

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
        center(b, "DMG " + (int) wd.damage + "   CD " + wd.cooldown + "s   x" + wd.count * wd.burst,
                cx, top - 220, UiTheme.A_LINE);
        String t = traits(wd);
        center(b, t.isEmpty() ? "-" : t, cx, top - 248, UiTheme.A_HINT);

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
        answer(b, "FLYING " + d.name + "   \u00B7   FITTED " + wd.name, false);
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
                b.draw(r, cx - 38, y1 - 62 - 38, 76, 76);
                b.setColor(1, 1, 1, 1);
            }
            if (sel) centerGold(b, hd.name, cx, y1 - 112, 1f);
            else center(b, hd.name, cx, y1 - 112, a);
            float sy = y1 - 136;
            if (h.shipIndex == hd.id) centerGold(b, "FLYING", cx, sy, 1f);
            else if (h.ownsShip(i) || locked) center(b, hullBuyLine(h, hd, loka), cx, sy, 0.7f);
            else priceCenter(b, cx, sy, hd.cost, 0, 0, "", h.coins >= hd.cost, 0.7f);
        }

        ShipDef sel = ShipDef.ALL[ship], fly = ShipDef.ALL[h.shipIndex];
        float head = fleetHeadY(n);
        left(b, "STAT", BODY_X + 8, head, UiTheme.A_HINT);
        right(b, "NOW", BODY_X + 270, head, UiTheme.A_HINT);
        right(b, "VS FLYING", BODY_X + 400, head, UiTheme.A_HINT);
        left(b, "UPGRADES", BODY_X + 500, head, UiTheme.A_HINT);
        for (int k = 0; k < 3; k++) {
            float cy = fleetStatY(n, k);
            left(b, STAT_NAMES[k], BODY_X + 8, cy, UiTheme.A_HINT);
            right(b, statStr(k, h, sel), BODY_X + 270, cy, 1f);
            float dv = statValue(k, h, sel) - statValue(k, h, fly);
            boolean same = sel == fly || Math.abs(dv) < 0.5f;
            right(b, same ? "-" : (dv > 0 ? "+" : "") + (int) dv, BODY_X + 400, cy,
                    !same && dv > 0 ? 1f : UiTheme.A_HINT);
            ShipDef.Upgrade u = sel.upgrades[k];
            left(b, u.label + "  " + u.name, BODY_X + 500, cy, UiTheme.A_LINE);
            float xr = BODY_X + BODY_W - 8;
            if (h.owned[ship][k]) right(b, "OWNED", xr, cy, 0.7f);
            else if (!h.ownsShip(ship)) right(b, "-", xr, cy, UiTheme.A_HINT);
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
                else if (!ownedHull) right(b, "-", xr, cy, UiTheme.A_HINT);
                else priceRight(b, xr, cy, d.upgrades[t].cost, t == 2 ? Hangar.SHARD_COST : 0, el, "",
                            tierAffordable(h, d, t), UiTheme.A_HINT);
            }
        }

        ShipDef.Upgrade u = sd.upgrades[tier];
        float bottom = refitBottom(n);
        leftGold(b, u.label + "   " + u.name + "   \u00B7   " + tierEffect(sd, tier), BODY_X + 8, bottom - 34, 1f);
        boolean owned = h.owned[ship][tier];
        String after = statAfter(0, h, sd, u, owned) + "     " + statAfter(1, h, sd, u, owned)
                + "     " + statAfter(2, h, sd, u, owned);
        left(b, after, BODY_X + 8, bottom - 62, UiTheme.A_LINE);

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
        right(b, "SHOTS", BODY_X + 450, hy, UiTheme.A_HINT);
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
            right(b, "x" + d.count * d.burst, BODY_X + 450, cy, UiTheme.A_LINE);
            float xr = BODY_X + PAT_TABLE_W - 8;
            if (h.weaponIndex == i) rightGold(b, "FITTED", xr, cy, 1f);
            else if (owned) right(b, "OWNED", xr, cy, 0.7f);
            else priceRight(b, xr, cy, d.cost, 0, 0, "", afford, UiTheme.A_HINT);
        }

        // the detail card
        WeaponDef sel = WeaponDef.ALL[weapon];
        boolean owned = h.weaponOwned[weapon];
        float cx = PAT_CARD_X + PAT_CARD_W / 2f;
        b.setColor(1, 1, 1, owned ? 1f : 0.6f);
        b.draw(cards.get(weapon), cx - 48, BODY_TOP - 72 - 48, 96, 96);
        b.setColor(1, 1, 1, 1);
        center(b, sel.name, cx, BODY_TOP - 150, 1f);
        float sy = BODY_TOP - 174;
        if (h.weaponIndex == weapon) centerGold(b, weaponState(h, sel), cx, sy, 1f);
        else if (owned) center(b, weaponState(h, sel), cx, sy, 0.7f);
        else priceCenter(b, cx, sy, sel.cost, 0, 0, "", h.coins >= sel.cost, UiTheme.A_HINT);

        float xl = PAT_CARD_X + 20, xr = M1 - 20, y = BODY_TOP - 222;
        for (String[] row : statRows(sel)) {
            if (textW(row[0]) + textW(row[1]) + 16 > xr - xl) { // long value: it takes its own line
                left(b, row[0], xl, y, UiTheme.A_HINT);
                y -= 22;
                right(b, row[1], xr, y, 1f);
            } else pair(b, row[0], row[1], xl, xr, y);
            y -= 26;
        }

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
        pair(b, "HELD", ok ? h.shards[el] + " " + World.TATVA[el].toLowerCase() : "-", xl, xr, rule - 78);
        pair(b, "BANKED", ok ? h.forgeProgress[loka] + "/" + Math.max(1, h.forgeNeed(loka)) : "-", xl, xr, rule - 104);
        pair(b, "CHARGES", ok ? Integer.toString(h.forgeCharges[loka]) : "-", xl, xr, rule - 130);
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