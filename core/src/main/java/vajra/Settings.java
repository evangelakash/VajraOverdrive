package vajra;

import com.badlogic.gdx.utils.Json;

/** The player's settings — screen-shake intensity, popup scale — as plain data. Ownership
 *  lives with the {@link vajra.profile.Profile} (each pilot has their own), which serializes
 *  them inside profiles.json; Settings itself is pure: serialize/deserialize are string in /
 *  string out so the roundtrip tests run headless, and out-of-range values clamp so a
 *  hand-edited file can't break feel. */
public final class Settings {
    public float shake = 1f;      // camera-shake multiplier, 0..1
    public float popupScale = 1f; // floating-text size multiplier, 0..2
    public float sfxVolume = 0.8f; // the sound effects' master volume, 0..1

    public static class Data {
        public float shake = 1, popupScale = 1, sfxVolume = 0.8f;
    }

    public static String serialize(Settings s) {
        Data d = new Data();
        d.shake = s.shake;
        d.popupScale = s.popupScale;
        d.sfxVolume = s.sfxVolume;
        return new Json().toJson(d);
    }

    /** Read a save back; out-of-range values clamp so a hand-edited file can't break feel. */
    public static void deserialize(Settings s, String json) {
        Data d;
        try {
            d = new Json().fromJson(Data.class, json);
        } catch (RuntimeException e) {
            return; // a corrupt file stays at defaults
        }
        if (d == null) return;
        s.shake = clamp(d.shake, 0, 1);
        s.popupScale = clamp(d.popupScale, 0, 2);
        s.sfxVolume = clamp(d.sfxVolume, 0, 1);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
