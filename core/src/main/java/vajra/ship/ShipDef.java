package vajra.ship;

/** Ship definition: price, loka gate, base stats, and three upgrade tiers.
 *
 *  <p>Ships are bought, not merely selected: Mk-1 is the hull you start a run in, the other
 *  six cost coins, and each loka opens more of them (LokaTable.unlockShip). Tier x.1 is the
 *  ship itself; x.2 and x.3 are bought on top of it, x.3 wanting coins AND shards.
 *
 *  <p>POWER SCALING: the tier magnitudes are set against the difficulty curve in
 *  {@link vajra.world.LokaTable}, not in a vacuum. Deeper lokas raise enemy hull and fire by
 *  hpScale/dmgScale, so a hull that has not been bought and tiered loses ground band by band.
 *  Tier 3 is worth roughly +40% hull or +35% damage — enough to matter against the next
 *  loka's curve, not enough to skip it.
 *
 *  <p>PACE: base accel and top speed are deliberately low. The game is tuned around a hull
 *  that takes a moment to cross a chamber, because every enemy fires ONE bullet and the fight
 *  is about reading it rather than outrunning it. Tier speed bonuses stay small (+45/+50) so
 *  nothing on sale turns this into a different game. */
public class ShipDef {
    public final int id;
    public final String name;
    public final String flavor;
    public final int cost;        // coins to buy the hull; 0 = the starting ship
    public final int minLoka;     // deepest-reached loka index that opens this hull (see LokaTable)
    public final float maxHp;
    public final float accel;
    public final float maxSpeed;
    public final float turnRate; // deg/s
    public final float boostMult;
    public final float dashCd;
    public final float r, g, b;
    /** Fraction of incoming damage ignored (0 = none, 1 = invulnerable). Tank hulls
     *  take real mitigation so they can survive deep-loka fire. */
    public final float damageReduction;
    public final Upgrade[] upgrades = new Upgrade[3];

    ShipDef(int id, String name, String flavor, int cost, int minLoka, float maxHp, float accel,
            float maxSpeed, float turnRate, float boostMult, float dashCd,
            float r, float g, float b, float damageReduction) {
        this.id = id;
        this.name = name;
        this.flavor = flavor;
        this.cost = cost;
        this.minLoka = minLoka;
        this.maxHp = maxHp;
        this.accel = accel;
        this.maxSpeed = maxSpeed;
        this.turnRate = turnRate;
        this.boostMult = boostMult;
        this.dashCd = dashCd;
        this.r = r;
        this.g = g;
        this.b = b;
        this.damageReduction = damageReduction;
    }

    public static class Upgrade {
        public final String label;   // "1.2"
        public final String name;
        public final int cost;
        public final float cooldownMult;
        public final float damageMult;
        public final float dashCdMult;
        public final float speedBonus;
        public final float turnBonus;
        public final float hullBonus;
        public final float shieldBonus;

        Upgrade(String label, String name, int cost, float cooldownMult, float damageMult, float dashCdMult,
                float speedBonus, float turnBonus, float hullBonus, float shieldBonus) {
            this.label = label;
            this.name = name;
            this.cost = cost;
            this.cooldownMult = cooldownMult;
            this.damageMult = damageMult;
            this.dashCdMult = dashCdMult;
            this.speedBonus = speedBonus;
            this.turnBonus = turnBonus;
            this.hullBonus = hullBonus;
            this.shieldBonus = shieldBonus;
        }

        static Upgrade base(String label, String name) {
            return new Upgrade(label, name, 0, 1f, 1f, 1f, 0f, 0f, 0f, 0f);
        }
    }

    private static ShipDef ship(int id, String name, String flavor, int cost, int minLoka,
                                float maxHp, float accel, float maxSpeed,
                                float turnRate, float boostMult, float dashCd, float r, float g, float b,
                                float damageReduction, Upgrade u2, Upgrade u3) {
        ShipDef d = new ShipDef(id, name, flavor, cost, minLoka, maxHp, accel, maxSpeed,
                turnRate, boostMult, dashCd, r, g, b, damageReduction);
        d.upgrades[0] = Upgrade.base((id + 1) + ".1", name);
        d.upgrades[1] = u2;
        d.upgrades[2] = u3;
        return d;
    }

    private static Upgrade up(String label, String name, int cost, float cooldownMult, float damageMult,
                              float dashCdMult, float speedBonus, float turnBonus, float hullBonus, float shieldBonus) {
        return new Upgrade(label, name, cost, cooldownMult, damageMult, dashCdMult, speedBonus, turnBonus, hullBonus, shieldBonus);
    }

    /** An identity scan for the wire: whose row of the ladder this hull is. */
    public static int indexOf(ShipDef d) {
        for (int i = 0; i < ALL.length; i++)
            if (ALL[i] == d) return i;
        return 0;
    }

    // Hull ladder: Mk-1 is free, the pair per loka rises in price and in how good it is, so
    // a run's coin finds a decision ("buy the better hull now, or bank toward Vajra-7").
    public static final ShipDef[] ALL = {
            ship(0, "Vajra Mk-1", "the first blade", 0, 0, 100, 1850, 375, 180, 1.8f, 1.2f, 1f, 0.84f, 0.3f, 0f,
                    up("1.2", "Rapid Cycler", 120, 0.82f, 1f, 1f, 0f, 0f, 0f, 0f),
                    up("1.3", "Aegis Plate", 300, 1f, 1f, 1f, 0f, 0f, 40f, 15f)),
            ship(1, "Kite Mk-2", "fast as rumor", 250, 0, 85, 2250, 435, 200, 2.0f, 1.0f, 0.3f, 0.8f, 1f, 0f,
                    up("2.2", "Light Frames", 180, 1f, 1f, 1f, 50f, 10f, 0f, 0f),
                    up("2.3", "Twin Link", 360, 0.80f, 1.25f, 1f, 0f, 0f, 0f, 0f)),
            ship(2, "Aegis Mk-3", "the wall that walks", 320, 1, 150, 1550, 330, 160, 1.6f, 1.4f, 0.4f, 1f, 0.5f, 0.08f,
                    up("3.2", "Reinforced Hull", 220, 1f, 1f, 1f, 0f, 0f, 60f, 0f),
                    up("3.3", "Bulwark", 440, 1f, 1f, 0.92f, 0f, 0f, 0f, 55f)),
            ship(3, "Hive Mk-4", "swarm logic", 380, 1, 100, 1850, 365, 180, 1.8f, 1.2f, 1f, 0.6f, 0.2f, 0f,
                    up("4.2", "Swarm Lens", 240, 0.80f, 1f, 1f, 0f, 0f, 0f, 0f),
                    up("4.3", "Seeker Chips", 480, 1f, 1.35f, 1f, 0f, 0f, 25f, 0f)),
            ship(4, "Lance Mk-5", "reads the gap", 460, 2, 95, 2000, 405, 220, 1.8f, 1.1f, 0.75f, 0.5f, 1f, 0f,
                    up("5.2", "Sharpened", 280, 1f, 1.30f, 1f, 0f, 0f, 0f, 0f),
                    up("5.3", "Overdrive", 560, 0.85f, 1f, 1f, 45f, 15f, 0f, 0f)),
            ship(5, "Mortar Mk-6", "arrives late, loudly", 560, 2, 120, 1700, 350, 170, 1.7f, 1.3f, 1f, 0.35f, 0.3f, 0.05f,
                    up("6.2", "Heavy Bands", 320, 1f, 1.35f, 1f, 0f, 0f, 0f, 0f),
                    up("6.3", "Quickload", 640, 0.76f, 1f, 0.80f, 0f, 0f, 0f, 0f)),
            ship(6, "Vajra-7", "the seventh halo", 700, 3, 110, 2350, 470, 240, 2.0f, 0.9f, 1f, 1f, 0.95f, 0f,
                    up("7.2", "Angel Protocol", 400, 0.82f, 1.25f, 1f, 0f, 0f, 0f, 0f),
                    up("7.3", "Seventh Halo", 800, 1f, 1f, 0.72f, 0f, 0f, 50f, 50f))
    };
}