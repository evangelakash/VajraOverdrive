package vajra.profile;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import java.util.ArrayList;
import vajra.SaveGame;
import vajra.world.World;

/** The roster of profiles on this machine and the one run-end funnel every exit passes
 *  through. Each user holds their own {@link Profile} — name, settings, career ledgers — and
 *  the roster remembers which is live. When the multiplayer game is built, a session maps
 *  each remote player to one of these; the shape does not change.
 *
 *  <p>{@link #endRun} is THE way a run ends: it folds the run into the active profile's
 *  campaign book and calls {@link SaveGame#finish} in the same breath, so the best-score
 *  file and the profile can never drift apart. Screens call it; nothing else calls
 *  SaveGame.finish by hand. */
public final class Profiles {
    public static final int VERSION = 1;
    public static final int MAX = 8;          // roster cap: profiles.json is untrusted input
    public static final int NAME_MAX = 12;    // the name line is drawn at LOCKUP_SIZE

    private static final ArrayList<Profile> all = new ArrayList<>();
    private static int sel;
    private static boolean loaded;

    public static synchronized Profile active() { load(); return all.get(sel); }
    public static synchronized Profile at(int i) { load(); return all.get(Math.floorMod(i, all.size())); }
    public static synchronized int count() { load(); return all.size(); }

    /** Steps the roster and remembers it. */
    public static synchronized void select(int dir) { load(); sel = Math.floorMod(sel + dir, all.size()); save(); }

    /** A brand-new pilot: created, named, and made live. */
    public static synchronized Profile create(String raw) {
        load();
        if (all.size() >= MAX) return all.get(sel);
        Profile p = new Profile();
        p.name = sanitize(raw, "PILOT " + all.size());
        all.add(p);
        sel = all.size() - 1;
        save();
        return p;
    }

    /** Renames the live pilot; a blank or over-long entry keeps the old name. */
    public static synchronized void rename(String raw) {
        load();
        Profile p = all.get(sel);
        p.name = sanitize(raw, p.name);
        save();
    }

    /** THE run exit: fold the run into the active profile and the save file as one event.
     *  A run is recorded ONCE: the run screen and the pause screen both end a run, and a
     *  second call must not add the same flight to the book twice. */
    public static synchronized void endRun(World w) {
        if (w.recorded) return;
        w.recorded = true;
        load();
        int kills = 0;
        for (int k : w.kills) kills += k;
        Profile p = all.get(sel);
        if (w.multiplayer) p.recordMultiplayer(kills, w.score.points, w.time());
        else p.recordRun(kills, w.score.points, w.time());
        SaveGame.finish(w.hangar, w.score.points);
        save();
    }

    /** The pilot names the screen shows: uppercase, spaced, filesystem-safe. */
    public static String sanitize(String raw, String fallback) {
        if (raw == null) return fallback;
        String s = raw.trim().toUpperCase().replaceAll("[^A-Z0-9 \\-]", "");
        if (s.isBlank()) return fallback;
        return s.length() > NAME_MAX ? s.substring(0, NAME_MAX).trim() : s;
    }

    // --- pure serialization, SaveGame's contract ------------------------------------------

    private static final Json JSON = new Json();
    static { JSON.setIgnoreUnknownFields(true); }

    public static class Data {
        public int version = VERSION;
        public int active;
        public ArrayList<Profile> profiles = new ArrayList<>();
    }

    public static String serialize() {
        Data d = new Data();
        d.active = sel;
        d.profiles = all;
        return JSON.toJson(d);
    }

    /** Load a roster from a string, diskless — the headless tests run through here. */
    public static void deserialize(String json) {
        Data d;
        try {
            d = JSON.fromJson(Data.class, json);
        } catch (RuntimeException e) {
            d = null;
        }
        if (d == null || d.version != VERSION || d.profiles == null || d.profiles.isEmpty()) {
            all.clear();
            all.add(new Profile());
            sel = 0;
            return;
        }
        all.clear();
        for (int i = 0; i < d.profiles.size() && i < MAX; i++) all.add(d.profiles.get(i));
        sel = Math.floorMod(d.active, all.size());
    }

    /** Wipe the in-memory roster (the tests' fresh start; the file is untouched). */
    static synchronized void resetForTest() { loaded = true; all.clear(); all.add(new Profile()); sel = 0; }

    private static FileHandle file() { return Gdx.app == null ? null : Gdx.files.local("profiles.json"); }

    private static void load() {
        if (loaded) return;
        loaded = true;
        FileHandle f = file();
        if (f != null && f.exists()) deserialize(f.readString());
        if (all.isEmpty()) all.add(new Profile());
        sel = Math.floorMod(sel, all.size());
    }

    public static synchronized void save() {
        FileHandle f = file();
        if (f != null) f.writeString(serialize(), false); // null: headless (tests) — no disk, no error
    }
}
