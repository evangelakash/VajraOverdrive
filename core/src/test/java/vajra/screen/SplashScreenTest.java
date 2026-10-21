package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The studio card's beat: black first while the window settles, the logo rising on top of it,
 *  a long hold to read the name, then a slow outro.
 *
 *  <p>{@code logoAlpha} is a pure function of time precisely so it can be checked without a GL
 *  context — and what needs checking is arithmetic, not pixels. */
class SplashScreenTest {
    // Boundaries are asserted with a delta: the constants are floats that the JUnit arguments
    // promote to double, so a computed "exactly DELAY + FADE_IN" can land a hair past the branch
    // the method folds in float. The curve is right; an exact comparison is not.
    private static final float D = 1e-4f;

    @Test
    void nothingIsDrawnThroughTheDelay() {
        assertEquals(0, SplashScreen.logoAlpha(0), D, "t=0");
        assertEquals(0, SplashScreen.logoAlpha(SplashScreen.DELAY * 0.5f), D, "mid delay");
        assertEquals(0, SplashScreen.logoAlpha(SplashScreen.DELAY), D, "right up to the delay");
    }

    @Test
    void theLogoRisesHoldsAndFalls() {
        assertEquals(0.5f, SplashScreen.logoAlpha(SplashScreen.DELAY
                + SplashScreen.FADE_IN * 0.5f), D, "halfway up the rise");
        assertEquals(1, SplashScreen.logoAlpha(SplashScreen.DELAY + SplashScreen.FADE_IN), D,
                "up once the rise finishes");
        assertEquals(1, SplashScreen.logoAlpha(SplashScreen.DELAY + SplashScreen.FADE_IN
                        + SplashScreen.HOLD * 0.5f), D, "and it holds through the beat");
        assertEquals(0.5f, SplashScreen.logoAlpha(SplashScreen.DELAY + SplashScreen.FADE_IN
                + SplashScreen.HOLD + SplashScreen.FADE_OUT * 0.5f), D, "halfway out");
        assertEquals(0, SplashScreen.logoAlpha(SplashScreen.TOTAL), D, "gone by the end");
    }

    /** The hold is the one number the whole card is built around: long enough to read a name. */
    @Test
    void theHoldIsLongAndTheOutroIsSlow() {
        assertEquals(5f, SplashScreen.HOLD, "asked for a five second hold");
        assertEquals(2f, SplashScreen.FADE_OUT, "asked for a two second outro");
        assertTrue(SplashScreen.FADE_OUT > SplashScreen.FADE_IN * 2f,
                "the outro is slower than the rise, so it eases out rather than dropping");
    }

    /** 1.5x smaller than the 560 the logo was drawn at before. */
    @Test
    void theLogoIsDrawnSmaller() {
        assertEquals(373f, SplashScreen.BOX, 0.5f, "560 / 1.5");
    }

    /** Straight multiply: a batch alpha is never out of range. */
    @Test
    void theCurveStaysInRange() {
        for (float t = 0; t <= SplashScreen.TOTAL + 2; t += 0.05f) {
            float a = SplashScreen.logoAlpha(t);
            assertTrue(a >= 0f && a <= 1f, "alpha out of range at t=" + t + ": " + a);
        }
    }

    @Test
    void itIsNotDoneEarly() {
        assertFalse(SplashScreen.done(0));
        assertFalse(SplashScreen.done(SplashScreen.TOTAL - 0.01f), "one frame short is not done");
        assertTrue(SplashScreen.done(SplashScreen.TOTAL));
        assertTrue(SplashScreen.done(SplashScreen.TOTAL + 5f), "skipped to the end counts as done");
    }

    /** The card must outlive the delay plus a readable hold - it is the first thing seen. */
    @Test
    void theWholeBeatIsLongEnoughToBeACard() {
        assertTrue(SplashScreen.TOTAL > 8f, "the card runs " + SplashScreen.TOTAL + "s");
    }
}