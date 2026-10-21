package vajra.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Contract for the universal popup system (ported from src_reboot v0.9.5). Guards against
 *  the "stuck popup" regression: a popup MUST expire after its lifetime, and the pool is a
 *  fixed ring that never grows. */
class PopupsTest {
    @Test
    void popupExpiresAfterItsLifetime() {
        Popups p = new Popups();
        p.push(new Popups.Spec().text("+50").at(0f, 0f).life(1f));
        assertTrue(p.anyActive());
        for (int i = 0; i < 61; i++) p.update(1f / 60f); // just over one sim-second
        assertFalse(p.anyActive(), "popup must vanish after its life");
    }

    @Test
    void punchInEasesToFullScale() {
        Popups p = new Popups();
        Popups.Spec s = new Popups.Spec().text("+10").scale(2f).intro(0.12f);
        p.push(s);
        Popups.Spec slot = live(p);
        assertEquals(1f, p.punchOf(slot), 0.01f, "at birth the punch is half scale");
        p.update(0.12f);
        assertEquals(2f, p.punchOf(slot), 0.01f, "past the intro it is full scale");
    }

    @Test
    void poolOverwritesOldestWithoutGrowing() {
        Popups p = new Popups();
        for (int i = 0; i < Popups.MAX + 10; i++)
            p.push(new Popups.Spec().text("t" + i).life(60f));
        assertEquals(Popups.MAX, p.activeCount(), "pool is a fixed ring - never grows");
    }

    @Test
    void theScriptureImageRidesThePush() {
        Popups p = new Popups();
        p.push(new Popups.Spec().text("THREEFOLD").img("tritiya").at(0f, 0f));
        assertEquals("tritiya", live(p).img, "the Devanagari key survives into the pooled slot");
        assertEquals("THREEFOLD", live(p).text);
    }

    @Test
    void multiplierTiersMapToTheirVerse() {
        assertEquals("dvitiya", World.tierArt(2));
        assertEquals("tritiya", World.tierArt(3));
        assertEquals("chaturth", World.tierArt(4));
        assertEquals("pancham", World.tierArt(5), "the ladder caps at five");
        assertEquals("pancham", World.tierArt(9), "and stays capped");
        assertEquals("TWOFOLD", World.tierWord(2));
        assertEquals("THREEFOLD", World.tierWord(3));
        assertEquals("FOURFOLD", World.tierWord(4));
        assertEquals("FIVEFOLD", World.tierWord(5));
        assertEquals("FIVEFOLD", World.tierWord(9));
    }

    private static Popups.Spec live(Popups p) {
        for (Popups.Spec s : p.slots()) if (s.text != null) return s;
        throw new AssertionError("no live slot");
    }
}
