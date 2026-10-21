package vajra.screen;

import com.badlogic.gdx.graphics.Color;

/** The four co-op colours. A pilot's roster id picks the hue — the host wears pink,
 *  cyan, yellow, white follow in join order — and the same key colours every place a
 *  pilot shows up: the lobby's table, the mark under their hull and the name over it. */
public final class SeatColors {
    public static final Color PINK = new Color(1f, 0.32f, 0.75f, 1f);
    public static final Color CYAN = new Color(0.30f, 1f, 1f, 1f);
    public static final Color YELLOW = new Color(1f, 0.92f, 0.25f, 1f);
    public static final Color WHITE = new Color(1f, 1f, 1f, 1f);

    private static final Color[] SEAT = {PINK, CYAN, YELLOW, WHITE};

    /** The hue of a seat id (0 = host). Ids wrap past four — the roster cap keeps it sane. */
    public static Color of(int id) { return SEAT[id & 3]; }

    /** READY reads green, NOT READY reads red — the lobby's one unambiguous pair. */
    public static final Color GO = new Color(0.35f, 1f, 0.5f, 1f);
    public static final Color WAIT = new Color(1f, 0.32f, 0.28f, 1f);

    private SeatColors() {}
}
