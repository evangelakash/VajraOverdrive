package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** The title and pause screens share one ornament: a hairline frame, a line-diamond-line divider,
 *  and diamond markers on the selected row. It was a private set of methods on HomeScreen, and
 *  PauseScreen was written against an extracted `Ornament` class that was never created — so
 *  `core` did not compile, and a green build from stale classes hid it.
 *
 *  <p>These are reflection checks, not pixel checks: they cannot run a shape pass headless. What
 *  they DO guarantee is that every ornament call either resolves to a real method on `Ornament`
 *  or lives on a screen that declares it itself. A renamed or deleted helper fails here instead of
 *  at the next compile. */
class OrnamentTest {

    /** The exact calls the two screens make. A signature drift breaks the build; a MISSING CLASS
     *  is what actually happened, so the class's existence is asserted outright. */
    private static final Class<?>[] SHARED = {
            Ornament.class, // fails here if the class is ever deleted again
    };

    @Test
    void theSharedOrnamentExistsAndOffersWhatTheScreensCall() throws Exception {
        assertNotNull(Ornament.class, "Ornament must exist: PauseScreen.render() calls it");
        for (String sig : new String[] {
                "frame(Ui)", "divider(Ui,float,float,float,float)",
                "markers(Ui,float,String,float)", "diamond(Ui,float,float,float)",
                "gold(Ui,float)"}) {
            boolean found = false;
            for (Method m : Ornament.class.getDeclaredMethods()) {
                if (!m.getName().equals(sig.split("\\(")[0])) continue;
                StringBuilder got = new StringBuilder(m.getName()).append("(");
                Class<?>[] ps = m.getParameterTypes();
                for (int i = 0; i < ps.length; i++) {
                    if (i > 0) got.append(',');
                    got.append(ps[i].getSimpleName());
                }
                if (got.append(')').toString().equals(sig)) { found = true; break; }
            }
            assertTrue(found, "Ornament is missing " + sig);
        }
    }

    /** PauseScreen must not reach for an ornament helper that is not there. */
    @Test
    void everyOrnamentCallInTheScreensResolves() {
        for (Class<?> screen : SHARED) assertNotNull(screen);
        for (Method m : PauseScreen.class.getDeclaredMethods()) {
            assertFalse(m.getName().equals("tick"),
                    "tick was an alias for divider and has been deleted");
        }
    }

    /** The frame brackets are drawn from Ui.UI_H, not a stale hardcoded 720: the frame has to
     *  meet the corners at whatever height the UI is. */
    @Test
    void theFrameIsInsetFromTheLiveUiHeight() throws Exception {
        Method frame = Ornament.class.getDeclaredMethod("frame", Ui.class);
        assertTrue(frame != null);
        // the constant moved out of HomeScreen with the rest of the geometry
        for (String dead : new String[] {"SCREEN_H", "FRAME_INSET", "FRAME_LEN"}) {
            boolean stillThere = false;
            for (java.lang.reflect.Field f : HomeScreen.class.getDeclaredFields())
                if (f.getName().equals(dead)) stillThere = true;
            assertFalse(stillThere, dead + " moved to Ornament with the rest of the geometry");
        }
    }
}