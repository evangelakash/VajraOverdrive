package vajra;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The settings roundtrip, pure string: mutate, serialize, pour into a fresh copy. */
class SettingsTest {
    @Test
    void freshSettingsAreFullIntensity() {
        Settings s = new Settings();
        assertEquals(1f, s.shake, 0.0001f);
        assertEquals(1f, s.popupScale, 0.0001f);
    }

    @Test
    void theSettingsRoundTrip() {
        Settings a = new Settings();
        a.shake = 0.25f;
        a.popupScale = 1.5f;
        Settings b = new Settings();
        Settings.deserialize(b, Settings.serialize(a));
        assertEquals(0.25f, b.shake, 0.0001f);
        assertEquals(1.5f, b.popupScale, 0.0001f);
    }

    @Test
    void outOfRangeValuesClampAndCorruptionStaysDefault() {
        Settings a = new Settings();
        a.shake = 9f;
        a.popupScale = -3f;
        Settings b = new Settings();
        Settings.deserialize(b, Settings.serialize(a));
        assertEquals(1f, b.shake, 0.0001f, "shake clamps to full");
        assertEquals(0f, b.popupScale, 0.0001f, "popup scale clamps to zero");
        Settings c = new Settings();
        Settings.deserialize(c, "{not json at all");
        assertEquals(1f, c.shake, 0.0001f, "a corrupt file stays at defaults");
    }
}
