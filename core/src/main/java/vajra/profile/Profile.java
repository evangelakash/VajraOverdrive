package vajra.profile;

import com.badlogic.gdx.utils.Json;
import vajra.Settings;

/** One player's everything that outlives a run: a name, their settings, and their career —
 *  the campaign ledger now, and the multiplayer ledger the moment that game exists.
 *
 *  <p>The shape is multiplayer-shaped on purpose: the two {@link Stats} books (CAMPAIGN and
 *  MULTIPLAYER) are kept apart so a multiplayer kill never muddies a campaign best and vice
 *  versa, and every field here is per-profile — two pilots on one machine hold two states,
 *  two settings, two ledgers. Serialization is pure (string in / string out) so the
 *  roundtrip tests run headless; only {@link Profiles} touches Gdx.files. */
public final class Profile {
    /** What a run pays into one book. `points` is career total, `best` the single-run top. */
    public static class Stats {
        public int runs, kills;
        public long points, best;
        public float seconds;   // time at the controls, across all runs

        public void copyFrom(Stats o) {
            runs = o.runs; kills = o.kills; points = o.points; best = o.best; seconds = o.seconds;
        }
    }

    public String name = "PILOT";
    public final Stats campaign = new Stats();
    public final Stats multiplayer = new Stats(); // written by a multiplayer flight
    public final Settings settings = new Settings();

    /** A finished run folds into the campaign book: one more run, its kills, its score and
     *  its seconds; `best` keeps only the top single run. An abandoned run still counts —
     *  quitting from pause is a run you flew. */
    public void recordRun(int kills, long points, float seconds) {
        campaign.runs++;
        campaign.kills += Math.max(0, kills);
        campaign.points += Math.max(0, points);
        campaign.best = Math.max(campaign.best, points);
        campaign.seconds += Math.max(0, seconds);
    }

    /** A flight with other pilots folds into the MULTIPLAYER book instead: a co-op kill must
     *  never muddy a campaign best, and a campaign run must never muddy a co-op one. */
    public void recordMultiplayer(int kills, long points, float seconds) {
        multiplayer.runs++;
        multiplayer.kills += Math.max(0, kills);
        multiplayer.points += Math.max(0, points);
        multiplayer.best = Math.max(multiplayer.best, points);
        multiplayer.seconds += Math.max(0, seconds);
    }

    // --- pure serialization, SaveGame's contract ------------------------------------------

    private static final Json JSON = new Json();
    static { JSON.setIgnoreUnknownFields(true); } // a save is untrusted input: skip strays

    public static class Data {
        public int version = 1;
        public String name;
        public Stats campaign, multiplayer;
        public String settings; // the settings' own JSON, so its clamps stay in one place
    }

    public static String serialize(Profile p) {
        Data d = new Data();
        d.name = p.name;
        d.campaign = p.campaign;
        d.multiplayer = p.multiplayer;
        d.settings = Settings.serialize(p.settings);
        return JSON.toJson(d);
    }

    /** Read a profile back; a corrupt file or a blank name keeps what the caller already had. */
    public static void deserialize(Profile p, String json) {
        Data d;
        try {
            d = JSON.fromJson(Data.class, json);
        } catch (RuntimeException e) {
            return;
        }
        if (d == null) return;
        if (d.name != null && !d.name.isBlank()) p.name = d.name;
        if (d.campaign != null) p.campaign.copyFrom(d.campaign);
        if (d.multiplayer != null) p.multiplayer.copyFrom(d.multiplayer);
        Settings.deserialize(p.settings, d.settings);
    }
}
