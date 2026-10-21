package vajra.screen;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.world.World;

/** The hull / shield / boost block: three thin bars huddled together in the top-left, drawn
 *  entirely in code — no image assets, no frame. Each bar is a dark track with a value fill
 *  clipped to the fraction; fills from the mask mockup. Below the bars: golden life pips.
 *  Right of the stack: a narrow dash pip filling bottom to top (full = dash ready).
 *  Geometry is public: the screenshot test samples it. */
public final class HudBars {
    // fills sourced from the shared theme
    public static final float[] HULL_C = { UiTheme.HULL_R, UiTheme.HULL_G, UiTheme.HULL_B };
    public static final float[] SHIELD_C = { UiTheme.SHIELD_R, UiTheme.SHIELD_G, UiTheme.SHIELD_B };
    public static final float[] BOOST_C = { UiTheme.BOOST_R, UiTheme.BOOST_G, UiTheme.BOOST_B };
    public static final float[] GOLD = { UiTheme.GOLD_R, UiTheme.GOLD_G, UiTheme.GOLD_B };
    private static final float TRACK_A = 0.55f; // unfilled interior

    // block geometry (public: the screenshot test samples it)
    public static final float X = 14, TOP = Ui.UI_H - 14, W = 220;
    public static final float HULL_H = 8, SHIELD_H = 3, BOOST_H = 6;
    public static final float GAP = 2; // bars huddled close
    public static final float SHIELD_TOP = TOP - HULL_H - GAP;
    public static final float BOOST_TOP = SHIELD_TOP - SHIELD_H - GAP;
    public static final float BOT = BOOST_TOP - BOOST_H; // block bottom edge

    public static final float LIVES_Y = BOT - 5, LIVES_R = 3, LIVES_PITCH = 9;
    public static final float DASH_X = X + W + 4, DASH_W = 4, DASH_BOT = BOT,
            DASH_H = TOP - BOT; // spans all three bars, fills bottom to top

    /** Track + value fill for the bars, the dash pip and the life pips — shapes pass. */
    public void shapes(ShapeRenderer s, World w) {
        bar(s, TOP, HULL_H, frac(w.ship.hp / w.ship.maxHp()), HULL_C);
        bar(s, SHIELD_TOP, SHIELD_H,
                frac(w.ship.shieldEnergy / w.ship.shieldCap()), SHIELD_C);
        bar(s, BOOST_TOP, BOOST_H, frac(w.ship.boostEnergy / 100f), BOOST_C);
        dash(s, w);
        lives(s, w);
    }

    /** One bar: dark track, then the value fill clipped to the fraction, left to right. */
    private void bar(ShapeRenderer s, float top, float h, float frac, float[] c) {
        float y = top - h;
        s.setColor(0, 0, 0, TRACK_A);
        s.rect(X, y, W, h);
        if (frac <= 0) return;
        s.setColor(c[0], c[1], c[2], 1);
        s.rect(X, y, W * frac, h);
    }

    /** Narrow pip right of the stack: recharge fills bottom to top, full = dash ready. */
    private void dash(ShapeRenderer s, World w) {
        float max = Math.max(0.001f, w.ship.dashCd());
        float frac = w.ship.dashCd <= 0 ? 1 : 1 - w.ship.dashCd / max;
        frac = Math.max(0, Math.min(1, frac));
        s.setColor(0, 0, 0, TRACK_A);
        s.rect(DASH_X, DASH_BOT, DASH_W, DASH_H);
        if (frac <= 0) return;
        s.setColor(GOLD[0], GOLD[1], GOLD[2], 1);
        s.rect(DASH_X, DASH_BOT, DASH_W, DASH_H * frac); // bottom to top
    }

    /** Golden life pips under the bars. */
    private void lives(ShapeRenderer s, World w) {
        s.setColor(GOLD[0], GOLD[1], GOLD[2], 1);
        for (int i = 0; i < w.lives; i++)
            s.circle(X + LIVES_R + i * LIVES_PITCH, LIVES_Y, LIVES_R);
    }

    private static float frac(float f) {
        return Math.max(0, Math.min(1, f));
    }
}
