package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The pause overlay is a title and three rows, and nothing else. It used to print the frozen
 *  run in three lines — ship, region, score — which duplicated what the HUD and the hangar
 *  already show, and the run is sitting right there behind the ink when you resume.
 *
 *  <p>So there is no behaviour left to pin here; what is worth pinning is that the three rows
 *  stay in order and that the run state stays gone. */
class PauseScreenTest {

    @Test
    void theRowsAreResumeSettingsQuit() {
        assertArrayEquals(new String[] {"RESUME", "SETTINGS", "QUIT TO TITLE"}, PauseScreen.ROWS);
    }

    /** The run state was removed on purpose. If someone re-adds a status block this fails, and
     *  they have to argue for it rather than have it drift back in. */
    @Test
    void theRunStateIsNotPrinted() {
        for (java.lang.reflect.Method m : PauseScreen.class.getDeclaredMethods()) {
            assertNotEquals("statusLines", m.getName(),
                    "the pause overlay prints no run state; drop this test's guard if that changes");
        }
        for (java.lang.reflect.Field f : PauseScreen.class.getDeclaredFields()) {
            assertFalse(f.getName().startsWith("STATUS"),
                    "no status block: " + f.getName() + " is left over");
        }
    }
}