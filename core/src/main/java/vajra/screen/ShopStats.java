package vajra.screen;

import vajra.ship.Hangar;
import vajra.ship.ShipDef;
import vajra.weapon.WeaponDef;

/** THE ARMOURY'S NUMBERS — every figure the hangar's charts draw, as plain maths with no GL, so
 *  the headless tests can pin what a chart claims against what the ship and the weapon really do.
 *
 *  <p>SHIPS. Six axes, each a real quantity from {@link vajra.ship.Ship}: hull is effective hit
 *  points (hull / (1 - damageReduction), the way {@code Ship.damage} applies it), shield is
 *  {@code 50 + bonus} ({@code Ship.shieldCap}), speed and agility are top speed and turn rate,
 *  dash is dashes per second, and gun is damage-mult / cooldown-mult — the same two factors
 *  {@code Weapon.fire} applies. Each axis is drawn as a fraction of the best any hull reaches
 *  with BOTH upgrades bought, so a ghost "fully upgraded" outline always fits the chart.
 *
 *  <p>WEAPONS. Damage per second is worked from how {@code Weapon} really fires: a pull's
 *  cycle is its cooldown, but a burst cannot cycle faster than its own length and a charge shot
 *  cannot cycle faster than its charge. A charge held to the full pays double ({@code
 *  Weapon.released}). Reach is range for a beam and speed x life for a bolt. */
public final class ShopStats {
    private ShopStats() {}

    // =============================================================== ships
    public static final int HULL = 0, SHIELD = 1, SPEED = 2, AGILITY = 3, DASH = 4, GUN = 5;
    public static final int AXES = 6;
    public static final String[] AXIS_NAMES = { "HULL", "SHIELD", "SPEED", "AGILITY", "DASH", "GUNS" };
    /** What one axis means, in the player's words — the legend under the chart. */
    public static final String[] AXIS_HELP = {
            "hit points after armour",
            "energy the shield can soak",
            "top speed",
            "how fast the nose turns",
            "dashes per second",
            "damage and fire rate together" };

    /** The shield every hull carries before any upgrade (Ship.shieldCap). */
    public static final float BASE_SHIELD = 50f;

    /** One hull's six raw values with the given upgrade tiers (tier 1 and 2; tier 0 is the hull). */
    public static float[] raw(ShipDef d, boolean tier1, boolean tier2) {
        float hull = d.maxHp, shield = BASE_SHIELD, speed = d.maxSpeed, turn = d.turnRate;
        float dashCd = d.dashCd, dmg = 1f, cd = 1f;
        for (int t = 1; t <= 2; t++) {
            if (!(t == 1 ? tier1 : tier2)) continue;
            ShipDef.Upgrade u = d.upgrades[t];
            hull += u.hullBonus;
            shield += u.shieldBonus;
            speed += u.speedBonus;
            turn += u.turnBonus;
            dashCd *= u.dashCdMult;
            dmg *= u.damageMult;
            cd *= u.cooldownMult;
        }
        float[] r = new float[AXES];
        r[HULL] = hull / Math.max(0.05f, 1f - d.damageReduction);
        r[SHIELD] = shield;
        r[SPEED] = speed;
        r[AGILITY] = turn;
        r[DASH] = 1f / Math.max(0.05f, dashCd);
        r[GUN] = dmg / Math.max(0.05f, cd);
        return r;
    }

    private static final float[] BEST = new float[AXES];

    static {
        for (ShipDef d : ShipDef.ALL) {
            float[] r = raw(d, true, true);
            for (int k = 0; k < AXES; k++) BEST[k] = Math.max(BEST[k], r[k]);
        }
    }

    /** The best any hull reaches on that axis, fully upgraded: the chart's outer ring. */
    public static float best(int axis) {
        return BEST[axis];
    }

    /** The six fractions (0..1) of the outer ring for a hull with the given tiers. */
    public static float[] fractions(ShipDef d, boolean tier1, boolean tier2) {
        float[] r = raw(d, tier1, tier2);
        for (int k = 0; k < AXES; k++) r[k] = Math.max(0f, Math.min(1f, r[k] / BEST[k]));
        return r;
    }

    /** A hull as the player owns it right now. */
    public static float[] fractions(Hangar h, ShipDef d) {
        return fractions(d, h.owned[d.id][1], h.owned[d.id][2]);
    }

    /** The raw value of an axis as the player reads it ("212 hp", "3.1 dash/s"). */
    public static String axisText(int axis, float raw) {
        switch (axis) {
            case HULL: return Math.round(raw) + " hp";
            case SHIELD: return Math.round(raw) + "";
            case SPEED: return Math.round(raw) + " px/s";
            case AGILITY: return Math.round(raw) + " deg/s";
            case DASH: return String.format(java.util.Locale.US, "%.2f/s", raw);
            default: return "x" + String.format(java.util.Locale.US, "%.2f", raw);
        }
    }

    /** One line on what a hull is FOR, drawn from its numbers rather than hand-written: its two
     *  strongest axes against the rest of the fleet. */
    public static String role(ShipDef d) {
        float[] f = fractions(d, true, true);
        int a = 0, b = -1;
        for (int k = 1; k < AXES; k++) if (f[k] > f[a]) a = k;
        for (int k = 0; k < AXES; k++) if (k != a && (b < 0 || f[k] > f[b])) b = k;
        return "STRONGEST: " + AXIS_NAMES[a] + " AND " + AXIS_NAMES[b];
    }

    // =============================================================== weapons
    /** What one pattern does, worked out from its definition and the hull that fires it. */
    public static final class Shot {
        public float perPull;   // damage one trigger pull can land if every projectile hits
        public float cycle;     // seconds between pulls when the trigger is held
        public float dps;       // perPull / cycle
        public float reach;     // px a projectile or beam travels
        public float speed;     // px/s of the projectile (beam: instant, reported as 0)
        public float spread;    // degrees across the fan, 0 for a single line
        public int rays;        // projectiles per volley fan
        public int pulses;      // bursts per pull
    }

    /** {@code dmgMult} and {@code cdMult} are the flying hull's ({@link Hangar#damageMult} and
     *  {@link Hangar#cooldownMult}); pass 1 for the pattern as it ships. */
    public static Shot shot(WeaponDef w, float dmgMult, float cdMult) {
        Shot s = new Shot();
        float power = w.chargeTime > 0 ? 2f : 1f; // held to the full, a charge pays 1 + charge/charge
        s.rays = Math.max(1, w.count);
        s.pulses = Math.max(1, w.burst);
        s.perPull = w.damage * dmgMult * power * s.rays * s.pulses;
        float cooldown = w.cooldown * cdMult;
        float burstLen = (s.pulses - 1) * w.burstGap;
        s.cycle = Math.max(0.03f, Math.max(cooldown, Math.max(burstLen, w.chargeTime)));
        s.dps = s.perPull / s.cycle;
        s.reach = w.beam ? w.range : w.speed * w.life;
        s.speed = w.beam ? 0f : w.speed;
        s.spread = s.rays > 1 ? w.spreadDeg : 0f;
        return s;
    }

    public static Shot shot(WeaponDef w) {
        return shot(w, 1f, 1f);
    }

    /** The pattern as the flying hull fires it. */
    public static Shot shot(Hangar h, WeaponDef w) {
        ShipDef d = ShipDef.ALL[h.shipIndex];
        return shot(w, h.damageMult(d), h.cooldownMult(d));
    }

    public static final int DPS = 0, ALPHA = 1, REACH = 2, RATE = 3;
    public static final String[] METRIC_NAMES = { "DAMAGE / SEC", "PER PULL", "REACH", "SHOTS / SEC" };
    public static final String[] METRIC_HELP = {
            "sustained damage, trigger held",
            "damage one pull can land",
            "how far it flies",
            "volleys per second" };

    private static final float[] TOP = new float[4];

    static {
        for (WeaponDef w : WeaponDef.ALL) {
            Shot s = shot(w);
            TOP[DPS] = Math.max(TOP[DPS], s.dps);
            TOP[ALPHA] = Math.max(TOP[ALPHA], s.perPull);
            TOP[REACH] = Math.max(TOP[REACH], s.reach);
            TOP[RATE] = Math.max(TOP[RATE], 1f / s.cycle);
        }
    }

    /** The best any pattern reaches on a metric as it ships: a full bar. */
    public static float top(int metric) {
        return TOP[metric];
    }

    public static float metric(Shot s, int metric) {
        switch (metric) {
            case DPS: return s.dps;
            case ALPHA: return s.perPull;
            case REACH: return s.reach;
            default: return 1f / s.cycle;
        }
    }

    /** The metric as a 0..1 bar. A hull upgrade can push a pattern past the best as it ships, so
     *  the bar is clamped rather than allowed off the track. */
    public static float bar(Shot s, int metric) {
        return Math.max(0f, Math.min(1f, metric(s, metric) / TOP[metric]));
    }

    public static String metricText(Shot s, int metric) {
        float v = metric(s, metric);
        switch (metric) {
            case REACH: return Math.round(v) + " px";
            case RATE: return String.format(java.util.Locale.US, "%.1f", v);
            default: return Integer.toString(Math.round(v));
        }
    }

    /** "x1.35" / "x0.80" / "same": how a figure sits against the one in hand, as the ratio of the
     *  two. A ratio and not a signed percentage because the game's body face has no + - or %. */
    public static String versus(float mine, float base) {
        if (base <= 0.0001f) return mine <= 0.0001f ? "same" : "new";
        if (versusPct(mine, base) == 0) return "same";
        return "x" + String.format(java.util.Locale.US, "%.2f", mine / base);
    }

    /** The signed percentage as a number, for colouring. */
    public static int versusPct(float mine, float base) {
        return base <= 0.0001f ? 0 : Math.round((mine / base - 1f) * 100f);
    }

    /** What a pattern is for, in a sentence — the part of a stat sheet people actually ask. */
    public static String howItPlays(WeaponDef w) {
        switch (w.name) {
            case "Single Bolt": return "One steady bolt. Reliable, and nothing to learn.";
            case "Double Bolt": return "A narrow pair of bolts. Forgiving aim, twice the damage.";
            case "Burst": return "Three quick shots down one line. Good against a single target.";
            case "Twin Burst": return "Four shots in two pairs. A wider burst that stays accurate.";
            case "Buckshot": return "A fan of five pellets. Brutal up close, thin at range.";
            case "Heavy Shell": return "One slow shell with real weight. Make each shot count.";
            case "Light MG": return "A stream of tiny rounds. Needs a steady hand; strips light foes.";
            case "Charge Shot": return "Hold to charge, release for up to double damage.";
            case "Laser": return "An instant beam. No travel time, stopped by rock.";
            case "Bomb": return "A slow bomb that bursts. Clears crowds, not single foes.";
            case "Missile": return "Homes in on a target. Fire and steer, then forget.";
            case "Tracking Missiles": return "Two missiles that turn sharply. Hard for anything to dodge.";
            case "Aim Shot": return "Nudges your aim toward foes within 20 degrees. Forgiving under pressure.";
            case "Piercer": return "Passes through foes, so a line of them takes every hit.";
            case "Wallbore": return "Passes through rock. Hit what is hiding behind the wall.";
            default: return "";
        }
    }
}
