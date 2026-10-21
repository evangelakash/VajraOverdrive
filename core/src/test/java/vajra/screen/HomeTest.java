package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The home screen is a plain menu: the only logic worth pinning is the wrapping cursor and
 *  that the seven rows stay in the order the test expects.
 *
 *  <p>Plus its vertical budget: the name, its divider, the arch's apex and the seven rows all
 *  share one column, and a collision (QUIT through the footer once) is only caught by a rendered
 *  frame or by these numbers, asserted against each other. */
class HomeTest {
    private static final float FRAME_TOP = Ui.UI_H - 28;  // the hairline frame's top edge
    private static final float HINT_TOP = UiTheme.FOOT_Y + 14;

    @Test
    void theMenuCursorWrapsBothWays() {
        assertEquals(1, HomeScreen.move(0, 1));
        assertEquals(6, HomeScreen.move(0, -1), "up from the top wraps to QUIT");
        assertEquals(0, HomeScreen.move(6, 1), "down from the bottom wraps to PLAY");
    }

    @Test
    void theRowsArePlayMultiplayerControlsProfileSettingsUpdateQuit() {
        assertArrayEquals(
                new String[] {"PLAY", "MULTIPLAYER", "CONTROLS", "PROFILE", "SETTINGS", "UPDATE", "QUIT"},
                HomeScreen.MENU);
    }

    /** The bottom row must clear the footer hint. This is the collision that actually happened. */
    @Test
    void theMenuClearsTheFooterHint() {
        float bottom = HomeScreen.menuY() - 6 * HomeScreen.pitch(); // the bottom of seven rows
        assertTrue(bottom - 14 > HINT_TOP + 10,
                "QUIT at y" + bottom + " runs into the hint at y" + HINT_TOP);
    }

    /** And the menu's top row clears the divider under the name. */
    @Test
    void theMenuClearsTheDivider() {
        assertTrue(HomeScreen.menuY() + 22 < HomeScreen.dividerY(),
                "the first row's caps run into the divider at y" + HomeScreen.dividerY());
    }

    /** A row's click zone must reach the next row's: no dead strips between rows at pitch 40. */
    @Test
    void theRowsAbutWithNoDeadZone() {
        assertEquals(HomeScreen.pitch(), 2 * HomeScreen.halfH(),
                "double the hit half-height must cover the pitch exactly");
    }

    /** The name sits under the valance and above the divider; the arch's apex sits between the
     *  divider and the first row, so the gateway frames the menu and never crosses the title. */
    @Test
    void theNameTheDividerAndTheMenuStackInOrder() {
        assertTrue(HomeScreen.dividerY() < HomeScreen.nameY(), "the divider is under the name");
        assertTrue(HomeScreen.nameY() < Ui.UI_H - 70, "the name hangs clear of the valance");
        assertTrue(HomeScreen.menuY() < HomeScreen.dividerY(), "the menu is under the divider");
    }
}
