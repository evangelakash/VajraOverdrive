package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.world.LokaTable;
import vajra.world.World;

/** THE HUD NEVER COLLIDES WITH ITSELF. The flight HUD is six blocks placed round the edge of the
 *  play: vitals, score, location and route, radar, and the armament console (and in co-op a crew
 *  slab). A rect that grows into a neighbour is only caught by a rendered frame, so these are the
 *  rects, asserted against each other, at their LARGEST (a running chain, five Astra cells, a
 *  four-pilot crew).
 *
 *  <p>Rects are {x0, y0, x1, y1} in UI space, y up. */
class HudLayoutTest {
    private static final float W = Ui.UI_W, H = Ui.UI_H;

    private static float[] vitals() {
        return new float[] { HudBars.X, HudBars.BOT, HudBars.X + HudBars.W, HudBars.TOP };
    }

    private static float[] crew() {
        return new float[] { Hud.CREW_X, Hud.CREW_TOP - (4 * Hud.CREW_PITCH + 14), Hud.CREW_X + Hud.CREW_W,
                Hud.CREW_TOP };
    }

    private static float[] score() {
        float x = (W - Hud.SCORE_W) / 2f, top = H - 14, h = Hud.SCORE_H + Hud.CHAIN_H;
        return new float[] { x, top - h, x + Hud.SCORE_W, top };
    }

    private static float[] location() {
        return new float[] { Hud.LOC_X, Hud.LOC_TOP - Hud.LOC_H, Hud.LOC_X + Hud.LOC_W, Hud.LOC_TOP };
    }

    private static float[] route() {
        return new float[] { Hud.ROUTE_X - 40, Hud.ROUTE_BOT - 12, Hud.ROUTE_X + 14, Hud.ROUTE_TOP + 12 };
    }

    private static float[] radar() {
        // the dial, its bezel scale, and the N above it
        float r = HudCompass.R + 14;
        return new float[] { HudCompass.CX - r, HudCompass.CY - r, HudCompass.CX + r, HudCompass.CY + r + 14 };
    }

    private static float[] console(int loka) {
        World w = new World();
        w.lokaIndex = loka;
        float x = Hud.consoleX(w);
        return new float[] { x, Hud.CONSOLE_Y, x + Hud.consoleW(w), Hud.CONSOLE_Y + Hud.CONSOLE_H };
    }

    private static boolean overlap(float[] a, float[] b) {
        return a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3];
    }

    private static void apart(String an, float[] a, String bn, float[] b) {
        assertFalse(overlap(a, b), an + " " + java.util.Arrays.toString(a) + " runs into " + bn
                + " " + java.util.Arrays.toString(b));
    }

    @Test
    void everyBlockIsOnScreen() {
        for (float[] r : new float[][] { vitals(), crew(), score(), location(), route(), radar(),
                console(0), console(6) }) {
            assertTrue(r[0] >= 0 && r[1] >= 0 && r[2] <= W && r[3] <= H,
                    "off screen: " + java.util.Arrays.toString(r));
        }
    }

    @Test
    void theTopRowDoesNotCollide() {
        apart("vitals", vitals(), "score", score());
        apart("score", score(), "location", location());
        apart("vitals", vitals(), "location", location());
    }

    @Test
    void theRightEdgeStacksLocationOverRoute() {
        apart("location", location(), "route", route());
        assertTrue(route()[3] < location()[1], "the route's head is under the tag");
    }

    @Test
    void theCrewHangsUnderTheVitalsAndClearsTheRadar() {
        apart("vitals", vitals(), "crew", crew());
        apart("crew", crew(), "radar", radar());
        assertTrue(crew()[3] <= vitals()[1], "the crew slab starts under the vitals");
    }

    @Test
    void theConsoleClearsTheRadarAndTheRouteAtItsWidest() {
        for (int loka = 0; loka < LokaTable.COUNT; loka++) {
            apart("console (loka " + loka + ")", console(loka), "radar", radar());
            apart("console (loka " + loka + ")", console(loka), "route", route());
        }
    }

    /** The middle of the screen is the fight. Nothing the HUD draws may reach into a box round it. */
    @Test
    void theCentreOfTheScreenIsLeftToThePlay() {
        float[] centre = { W / 2f - 220, H / 2f - 130, W / 2f + 220, H / 2f + 130 };
        for (float[] r : new float[][] { vitals(), score(), location(), route(), radar(), console(6) })
            apart("the HUD", r, "the play area", centre);
    }

    @Test
    void theVitalsMetersSitInsideTheirSlab() {
        for (int i = 0; i < 4; i++) {
            float y = HudBars.rowY(i);
            assertTrue(y - HudBars.BAR_H / 2f > HudBars.BOT && y + HudBars.BAR_H / 2f < HudBars.TOP);
        }
        assertTrue(HudBars.BAR_X + HudBars.BAR_W < HudBars.X + HudBars.W - 40, "room for the figure on the right");
        assertTrue(HudBars.LIVES_Y > HudBars.BOT && HudBars.LIVES_Y < HudBars.rowY(3), "the lives row is last");
    }
}
