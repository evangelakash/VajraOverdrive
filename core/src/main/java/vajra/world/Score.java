package vajra.world;

import java.util.Locale;
import vajra.enemy.Enemy;

/** SCORE & COMBO — the run's ledger. A kill chains into a timed combo that multiplies itself;
 *  milestones (leader break, formation wipe, region entry, checkpoint, boss) pay flat and never
 *  touch the chain. Kill values ride on data every enemy already carries — kind hp plus the
 *  dropLoot tier vocabulary — so future kinds score without a new table. Every add saturates
 *  at MAX: the ten displayed digits can never overflow. */
public final class Score {
    public static final long MAX = 9_999_999_999L; // what the ten digits can show
    public static final float WINDOW = 3f;          // seconds between kills to hold the chain
    public static final int[] TIER = { 50, 250, 600, 3000 }; // rank0 / rank1 / elite / boss
    public static final int LEADER = 250;  // a rank-1 leader falls
    public static final int WIPE = 500;    // the last unit of a formation falls
    public static final int REGION = 500;  // first crossing into a band
    public static final int CHECKPOINT = 250;
    public static final int BOSS = 5000;   // Bala himself (on top of his kill value)

    public long points;
    public int combo;     // kills in the current chain
    public float comboT;  // seconds left of it

    /** x1 -> x2 at 5 -> x3 at 10 -> x4 at 15 -> x5 at 20, capped. */
    public int mult() {
        return 1 + Math.min(4, combo / 5);
    }

    public void update(float dt) {
        if (comboT <= 0) return;
        comboT -= dt;
        if (comboT <= 0) { combo = 0; comboT = 0; } // chain dropped: back to x1
    }

    public void breakCombo() { // death: the chain dies with you
        combo = 0;
        comboT = 0;
    }

    /** One player kill (from World.countKill, the funnel every player death runs through):
     *  bump the chain first so the new tier pays now, score, then the formation bookkeeping —
     *  leader break and wipe are flat: no multiplier, no chain extension. */
    public void kill(World w, Enemy e) {
        combo++;
        comboT = WINDOW;
        int tier = e.boss ? 3 : e.elite ? 2 : e.rank == 1 ? 1 : 0; // the dropLoot tiers
        add((long) (TIER[tier] + Math.round(e.kind.hp)) * mult());
        if (!e.boss && e.rank == 1) add(LEADER);
        if (e.commander != null) wipe(w, e);
    }

    /** Flat bonus (region / checkpoint / boss): pays without touching the chain. */
    public void add(long g) {
        points = Math.min(MAX, points + Math.max(0, g));
    }

    /** The formation fell: its leader is already dead and no squadmate is left. A leader dying
     *  over living members is a break (+LEADER), not a wipe — someone still answers the post. */
    private void wipe(World w, Enemy e) {
        if (!e.commander.dead) return;
        for (Enemy o : w.enemies)
            if (!o.dead && o.commander == e.commander) return; // someone's still flying
        add(WIPE);
    }

    /** The ten-digit display string. */
    public String digits() {
        return String.format(Locale.ROOT, "%010d", points);
    }
}
