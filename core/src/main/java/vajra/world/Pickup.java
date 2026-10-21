package vajra.world;

/** Loot that popped out of a dying enemy: a COIN (art variant), HEALTH, a SHARD (variant =
 *  tatva) or the loka's Vajrayudha PIECE (variant = loka). No gravity — the pop drifts a
 *  body-wide radius, decelerates exponentially (stop distance = v0 / DRAG), then floats until
 *  the hull sweeps it up (the magnet gives everything a faint tug on the way).
 *
 *  <p>LIFETIME: nothing stays in the world forever. {@link #life} counts down and the last
 *  {@link #FADE} seconds are the window a renderer draws it dimmer, so a band you cleared and
 *  walked away from empties itself instead of accumulating permanent clutter. */
public class Pickup {
    public static final int COIN = 0, HEALTH = 1, SHARD = 2, PIECE = 3;
    public static final int VARIANTS = 5;     // coin_1..coin_5 art
    public static final float DRAG = 3f;      // px/s decay: stop distance = v0 / DRAG
    public static final float COLLECT_R = 20; // hull half (12) + coin half (8): collected on contact
    public static final float LIFE = 40f;    // seconds on the ground before it fades out
    public static final float FADE = 4f;     // the tail of its life, drawn dimming

    public int type;
    public int variant;  // COIN: which of the five coin images
    public float x, y, vx, vy;
    public float phase;  // bob offset, visual only
    public float life = LIFE;
    // CO-OP: a ground-ghost. netId is the host's drop ledger; a guest's copy glides to it.
    public int netId;
    public float gx, gy;
    public boolean seen;

    public void update(float dt) {
        if (vx == 0 && vy == 0) return; // settled: floats in place
        x += vx * dt;
        y += vy * dt;
        float k = (float) Math.exp(-DRAG * dt);
        vx *= k;
        vy *= k;
        if (vx * vx + vy * vy < 1) vx = vy = 0;
    }

    /** 1 while fresh, easing to 0 over the last FADE seconds. Pure: the renderer reads it. */
    public float alpha() {
        return life >= FADE ? 1f : Math.max(0f, life / FADE);
    }
}
