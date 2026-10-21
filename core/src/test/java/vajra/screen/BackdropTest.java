package vajra.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Star wrap tiling — the only piece of the backdrop with arithmetic worth pinning. */
class BackdropTest {
    @Test
    void tileWrapsIntoSpan() {
        assertEquals(10f, Backdrop.tilePos(10f, 0f, 100f), 0.001f);
        assertEquals(90f, Backdrop.tilePos(-10f, 0f, 100f), 0.001f);
        assertEquals(5f, Backdrop.tilePos(105f, 0f, 100f), 0.001f);
        float p = Backdrop.tilePos(37f, 1000f, 50f);
        assertEquals(1037f, p, 0.001f, "origin shifts the window, not the value");
    }
}
