package vajra.ship;

import vajra.weapon.WeaponDef;
import vajra.world.LokaTable;

/** The purse, the shard stock, what you own, and what you have forged.
 *
 *  <p>A RUN OWNS NOTHING ON EXIT. Coins, tiers, patterns and pieces are all built
 *  inside a run and die with it — starting a new game resets the hangar to its defaults. The
 *  only thing that outlives a run is {@link #best}, the high score, which is a record rather
 *  than progress.
 *
 *  <p>Coins buy magnitudes (hulls and their tiers). Shards buy behavior — either as a tatva
 *  loka's forged weapon ({@link #forgeCharges}).
 */
public class Hangar {
    public static final int SHARD_COST = 3;  // x.3 tiers want shards on top of their coin price
    public static final int SHARD_SELL = 20; // coins per shard
    // tatva indexes, == World.TATVA order
    public static final int AKASHA = 0, VAYU = 1, AGNI = 2, JALA = 3, PRITHVI = 4;

    /** Shards of the loka's own element that FORGE the weapon - the one big payment. */
    public static final int FORGE_COST = 5;
    /** Further shards per stored release, once the weapon exists. */
    public static final int CHARGE_COST = 5;

    public int coins = 0; // a new run is broke — every coin is earned inside it
    public long best;     // the one thing that persists: best run score
    public int loka = 0;  // deepest loka this run has reached (index into LokaTable)
    // Magnetism: the pickup tug. Fixed at 15 for dev builds so loot actually reaches the hull.
    public float magnet = 15f;
    public final int[] shards = new int[5];         // Pancha Tatva stock (index = World.TATVA)
    public final boolean[] pieces = new boolean[7]; // Vajrayudha: one per loka
    public final int[] forgeProgress = new int[LokaTable.COUNT]; // shards banked toward the next forge/charge
    public final int[] forgeCharges = new int[LokaTable.COUNT];   // releases left on that loka's weapon
    public final boolean[] forged = new boolean[LokaTable.COUNT]; // forged ONCE, permanently
    public final boolean[][] owned = new boolean[ShipDef.ALL.length][3];
    public final boolean[] weaponOwned = new boolean[WeaponDef.ALL.length];
    public int shipIndex;
    public int weaponIndex;

    public Hangar() {
        owned[0][0] = true;   // the starting hull comes with the run
        weaponOwned[0] = true; // and the Single Bolt
    }

    /** True when this run has bought the hull (x.1 is the ship itself). */
    public boolean ownsShip(int i) {
        return i >= 0 && i < owned.length && owned[i][0];
    }

    /** N in the hangar: buy a hull outright. The loka must have opened it, and its price is
     *  on top of whatever tier the ship already has. Returns what it cost, 0 for a no-op. */
    public int buyShip(int i, int lokaReached) {
        if (i <= 0 || i >= owned.length || ownsShip(i)) return 0; // 0 is the starting hull
        if (!LokaTable.shipUnlocked(lokaReached, i)) return 0;
        ShipDef d = ShipDef.ALL[i];
        if (coins < d.cost) return 0;
        coins -= d.cost;
        owned[i][0] = true;
        return d.cost;
    }

    /** N in the hangar: unlock that pattern at its price. */
    public boolean buyWeapon(int i) {
        if (i < 0 || i >= weaponOwned.length || weaponOwned[i]) return false;
        int cost = WeaponDef.ALL[i].cost;
        if (coins < cost) return false;
        coins -= cost;
        weaponOwned[i] = true;
        return true;
    }

    /** The next owned pattern in dir (+1/-1), wrapping — what the V/B hotkeys walk. */
    public int cycleWeapon(int from, int dir) {
        int n = WeaponDef.ALL.length;
        for (int step = 1; step <= n; step++) {
            int i = Math.floorMod(from + dir * step, n);
            if (weaponOwned[i]) return i;
        }
        return from;
    }

    /** The next OWNED hull in dir, wrapping — the 1-7 hotkey only ever lands on something bought. */
    public int cycleShip(int from, int dir) {
        int n = ShipDef.ALL.length;
        for (int step = 1; step <= n; step++) {
            int i = Math.floorMod(from + dir * step, n);
            if (ownsShip(i)) return i;
        }
        return from;
    }

    public int shardCount() {
        int n = 0;
        for (int s : shards) n += s;
        return n;
    }

    public boolean buy(ShipDef d, int tier) {
        if (tier < 1 || tier > 2 || !ownsShip(d.id) || owned[d.id][tier]) return false;
        int cost = d.upgrades[tier].cost;
        if (coins < cost) return false;
        if (tier == 2 && shardCount() < SHARD_COST) return false;
        coins -= cost;
        if (tier == 2) takeShards(SHARD_COST);
        owned[d.id][tier] = true;
        return true;
    }

    /** Spend from the deepest stack first, so mixed stock drains evenly. */
    private void takeShards(int n) {
        while (n > 0) {
            int best = 0;
            for (int i = 1; i < shards.length; i++) if (shards[i] > shards[best]) best = i;
            shards[best]--;
            n--;
        }
    }

    /** X in the hangar: dump the whole stock at the flat rate. Returns the payout. */
    public int sellShards() {
        int paid = shardCount() * SHARD_SELL;
        coins += paid;
        for (int i = 0; i < shards.length; i++) shards[i] = 0;
        return paid;
    }

    /** THE FORGED WEAPON: only the loka's OWN element feeds it (LOKA-LAWS 3), so the forge
     *  has the same purity rule as always: only the loka's own element pays. The first FORGE_COST
     *  banks the weapon itself, and every CHARGE_COST after that buys a release.
     *
     *  <p>`forged` is separate from the charge count on purpose: forging ONCE is permanent, so
     *  spending your only charge must not drop you back to paying FORGE_COST for the next one. */
    public int addShard(int tatva, int lokaIndex) {
        if (tatva < 0 || tatva >= shards.length) return 0;
        shards[tatva]++;
        if (!LokaTable.at(lokaIndex).pays(tatva)) return 0;
        forgeProgress[lokaIndex]++;
        int gained = 0;
        for (int guard = 0; guard < 64; guard++) {
            if (!forged[lokaIndex]) {
                if (forgeProgress[lokaIndex] < FORGE_COST) break;
                forgeProgress[lokaIndex] -= FORGE_COST;
                forged[lokaIndex] = true;
                forgeCharges[lokaIndex]++;  // forging it hands you the FIRST release
                gained++;
                continue;
            }
            if (forgeProgress[lokaIndex] < CHARGE_COST) break;
            forgeProgress[lokaIndex] -= CHARGE_COST;
            forgeCharges[lokaIndex]++;
            gained++;
        }
        return gained;
    }

    /** Has this loka's weapon been forged? Permanent once true - spending a release cannot
     *  unmake it, or the next FORGE_COST would be owed all over again. */
    public boolean isForged(int lokaIndex) {
        return lokaIndex >= 0 && lokaIndex < forged.length && forged[lokaIndex];
    }

    /** What the next shard banks toward: forging the weapon, or one more release. */
    public int forgeNeed(int lokaIndex) {
        return isForged(lokaIndex) ? CHARGE_COST : FORGE_COST;
    }


    public boolean hasForgeCharge(int lokaIndex) {
        return forgeCharges[lokaIndex] > 0;
    }

    public boolean spendForgeCharge(int lokaIndex) {
        if (forgeCharges[lokaIndex] <= 0) return false;
        forgeCharges[lokaIndex]--;
        return true;
    }

    public float cooldownMult(ShipDef d) {
        float m = 1;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) m *= d.upgrades[t].cooldownMult;
        return m;
    }

    public float damageMult(ShipDef d) {
        float m = 1;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) m *= d.upgrades[t].damageMult;
        return m;
    }

    public float dashCdMult(ShipDef d) {
        float m = 1;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) m *= d.upgrades[t].dashCdMult;
        return m;
    }

    public float speedBonus(ShipDef d) {
        float s = 0;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) s += d.upgrades[t].speedBonus;
        return s;
    }

    public float turnBonus(ShipDef d) {
        float s = 0;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) s += d.upgrades[t].turnBonus;
        return s;
    }

    public float hullBonus(ShipDef d) {
        float h = 0;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) h += d.upgrades[t].hullBonus;
        return h;
    }

    public float shieldBonus(ShipDef d) {
        float s = 0;
        for (int t = 1; t <= 2; t++) if (owned[d.id][t]) s += d.upgrades[t].shieldBonus;
        return s;
    }
}