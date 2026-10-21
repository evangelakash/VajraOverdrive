package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The typing field's law: the OS characters build the line, ENTER closes first and
 *  then confirms, backspace erases, escape abandons without confirming, and an open
 *  field eats navigation so the menu can't wander under the pilot's fingers. This
 *  exists because the platform dialog turned out to be a no-op on LWJGL3. */
class LineInputTest {

    @Test
    void typingBuildsTheLineAndEnterConfirms() {
        LineInput l = new LineInput();
        String[] done = { null };
        l.open("JOIN CODE", "", t -> done[0] = t);
        assertTrue(l.typed('G'));
        assertTrue(l.typed('O'));
        assertEquals("GO", l.text());
        assertTrue(l.key(com.badlogic.gdx.Input.Keys.BACKSPACE));
        assertEquals("G", l.text());
        assertTrue(l.key(com.badlogic.gdx.Input.Keys.ENTER));
        assertEquals("G", done[0]);
        assertFalse(l.isActive(), "the line closed before the callback runs");
    }

    @Test
    void escapeAbandonsWithoutConfirming() {
        LineInput l = new LineInput();
        String[] done = { null };
        l.open("JOIN CODE", "", t -> done[0] = t);
        assertTrue(l.typed('A'));
        assertTrue(l.key(com.badlogic.gdx.Input.Keys.ESCAPE));
        assertFalse(l.isActive());
        assertNull(done[0], "an abandoned line never admits its text");
    }

    @Test
    void anOpenFieldEatsNavigationButEnterClosesIt() {
        LineInput l = new LineInput();
        String[] done = { null };
        l.open("JOIN CODE", "", t -> done[0] = t);
        assertTrue(l.key(com.badlogic.gdx.Input.Keys.W), "W would wander the rows");
        assertTrue(l.key(com.badlogic.gdx.Input.Keys.S));
        assertTrue(l.isActive(), "still open: navigation belongs to the field");
        assertTrue(l.key(com.badlogic.gdx.Input.Keys.ENTER));
        assertFalse(l.isActive(), "even an empty confirm closes the line first");
        assertEquals("", done[0]);
    }

    @Test
    void aClosedFieldHandlesNothing() {
        LineInput l = new LineInput();
        assertFalse(l.isActive());
        assertFalse(l.typed('a'));
        assertFalse(l.key(com.badlogic.gdx.Input.Keys.ENTER));
    }
}
