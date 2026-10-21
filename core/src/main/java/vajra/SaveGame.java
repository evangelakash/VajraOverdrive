package vajra;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;
import vajra.ship.Hangar;
import vajra.world.Score;

/** The save: ONE number.
 *
 *  <p>A run owns nothing on exit — coins, tiers, patterns, pieces and forged
 *  charges are all built inside a run and die with it, and starting a new game resets the
 *  hangar to its defaults. The single thing that outlives a run is {@code best}, the high
 *  score, which is a record rather than progress.
 *
 *  <p>Serialization is pure (string in / string out) so the roundtrip tests run headless;
 *  only store() and load() touch Gdx.files. */
public final class SaveGame {
    public static final int VERSION = 2; // v1 carried the whole hangar; v2 carries only `best`
    // libGDX Json THROWS on an unknown key by default, which would let one stray field in a
    // hand-edited file discard the entire record. A save file is untrusted input: skip what we
    // do not know rather than refusing to read it.
    private static final Json JSON = new Json();
    static { JSON.setIgnoreUnknownFields(true); }

    private SaveGame() {}

    /** The persisted slice: the best run score and nothing else. */
    public static class Data {
        public int version = VERSION;
        public long best;
    }

    /** Fold a finished run's score into the record. True when it beat the old best.
     *
     *  <p>Every path that ends a run must call this before store(). Ending the run normally and
     *  abandoning it from the pause screen are different exits but the same event: the score is
     *  spent either way, and the one number the game keeps should not depend on which exit you
     *  happened to take. Pure, so the roundtrip tests cover it headless. */
    public static boolean record(Hangar h, long points) {
        if (points <= h.best) return false;
        h.best = Math.min(points, Score.MAX);
        return true;
    }

    /** The one exit a finished run takes: fold the score into the record, then store. Every
     *  run-end calls THIS — the record/store pair is never called by hand again. */
    public static void finish(Hangar h, long points) {
        record(h, points);
        store(h);
    }

    public static String serialize(Hangar h) {
        Data d = new Data();
        d.best = h.best;
        return JSON.toJson(d);
    }

    /** Copy the saved record into a live Hangar, leaving everything else at its defaults.
     *  A corrupt file or a foreign version is ignored, not fatal. */
    public static void deserialize(Hangar h, String json) {
        Data d;
        try {
            d = JSON.fromJson(Data.class, json);
        } catch (RuntimeException e) {
            return; // a corrupt file starts broke, not broken
        }
        if (d == null || d.version != VERSION) return;
        h.best = Math.max(0, Math.min(Score.MAX, d.best));
    }

    private static FileHandle file() {
        return Gdx.files.local("vajra.save");
    }

    public static void store(Hangar h) {
        file().writeString(serialize(h), false);
    }

    public static void load(Hangar h) {
        FileHandle f = file();
        if (f.exists()) deserialize(h, f.readString());
    }
}