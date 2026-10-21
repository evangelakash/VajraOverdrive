package vajra.profile;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vajra.Settings;

/** The pilot's book, headless: the roundtrip contract SaveGame set (string in / string out),
 *  the run fold, name sanitizing, and the roster's wrap + tolerant read. Nothing here
 *  touches Gdx.files. */
class ProfileTest {

    @BeforeEach
    void fresh() {
        Profiles.resetForTest();
    }

    @Test
    void aRunFoldsIntoTheCampaignBook() {
        Profile p = Profiles.active();
        p.recordRun(7, 500, 30f);
        p.recordRun(3, 900, 12f); // a better run: best moves, totals add
        assertEquals(2, p.campaign.runs);
        assertEquals(10, p.campaign.kills);
        assertEquals(1400, p.campaign.points);
        assertEquals(900, p.campaign.best);
        assertEquals(42f, p.campaign.seconds, 0.001f);
        assertEquals(0, p.multiplayer.runs, "the multiplayer book stays shut until that game exists");
    }

    @Test
    void negativeReadsCannotPunishABook() {
        Profile p = Profiles.active();
        p.recordRun(-5, -100, -10f);
        assertEquals(1, p.campaign.runs, "the run still happened");
        assertEquals(0, p.campaign.kills);
        assertEquals(0, p.campaign.points);
    }

    @Test
    void theProfileRoundtripsThroughItsJson() {
        Profile p = Profiles.active();
        p.name = "AGNI-2";
        p.recordRun(12, 3456, 75f);
        p.settings.shake = 0.5f;
        p.settings.popupScale = 1.5f;

        Profile q = new Profile();
        Profile.deserialize(q, Profile.serialize(p));

        assertEquals("AGNI-2", q.name);
        assertEquals(1, q.campaign.runs);
        assertEquals(12, q.campaign.kills);
        assertEquals(3456, q.campaign.best);
        assertEquals(0.5f, q.settings.shake, 0.001f);
        assertEquals(1.5f, q.settings.popupScale, 0.001f);
    }

    @Test
    void theRosterRoundtripsAndRemembersWhosLive() {
        Profiles.create("AGNI");
        Profiles.create("VARUNA"); // active
        Profiles.select(-1);       // wrap backwards: live is AGNI

        Profiles.deserialize(Profiles.serialize());

        assertEquals(3, Profiles.count(), "the default PILOT + the two created");
        assertEquals("AGNI", Profiles.active().name, "the live pick survives the write");
    }

    @Test
    void namesAreSanitizedOnTheWayIn() {
        assertEquals("AGNI 2", Profiles.sanitize(" agni 2! ", "x"));
        assertEquals("THIRTEENCHAR", Profiles.sanitize("thirteenchars", "x"), "12 max");
        assertEquals("FALLBACK", Profiles.sanitize("   @@@ ", "FALLBACK"), "blank keeps the old");
        assertEquals("FALLBACK", Profiles.sanitize(null, "FALLBACK"), "null keeps the old");
    }

    @Test
    void aCorruptFileFallsBackToOneFreshPilot() {
        Profiles.create("VARUNA");
        Profiles.deserialize("{\"version\":1,\"junk\":true}"); // empty profile list
        assertEquals(1, Profiles.count());
        assertEquals("PILOT", Profiles.active().name, "the fresh default pilot");
        Profiles.deserialize("not json at all");
        assertEquals(1, Profiles.count(), "a corrupt file is a default, never a crash");
    }

    @Test
    void settingsDeserializationClampsTheFeel() {
        Settings s = new Settings();
        Settings.deserialize(s, "{\"shake\":9,\"popupScale\":-3}");
        assertEquals(1f, s.shake, 0.001f, "shake 0..1");
        assertEquals(0f, s.popupScale, 0.001f, "popup 0..2");
    }
}
