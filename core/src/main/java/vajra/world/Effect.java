package vajra.world;

/** Timed visual effect. SPARK: fading dot that can fly and slow down. RING: expanding circle.
 *  FLASH: beam line. SMOKE: 4-frame sprite puff that drifts, billows and fades.
 *  SURGE: an expanding FLOOD — a filled body of water that rushes out, fills its radius and
 *  then dissipates. Not a ring: the ring is the crest on its front, this is the volume behind it. */
public class Effect {
    public static final int SPARK = 0, RING = 1, FLASH = 2, SMOKE = 3, SURGE = 4;
    public int type;
    public float x, y, x2, y2;
    public float vx, vy, drag; // SPARK motion: px/s and per-second exponential decay
    public float phase, wobble; // SPARK: sideways sine, amplitude px/s, dies off with age
    public float age, life, radius, r, g, b;
    /** SURGE: how long the front spends travelling, as a share of `life`. After that the body
     *  holds its full radius and just fades — a wave that reached the walls, not one still going. */
    public float travel = 0.45f;
    /** SURGE: fill alpha at full strength. Low — a body of water, not a white flash. */
    public float bodyAlpha = 0.22f;

    public static Effect spark(float x, float y, float r, float g, float b) {
        return spark(x, y, 0, 0, 0, 0.2f, 5, r, g, b);
    }

    /** A moving, fading dot: velocity in px/s, drag decays it, size in px. */
    public static Effect spark(float x, float y, float vx, float vy, float drag,
                               float life, float size, float r, float g, float b) {
        Effect e = new Effect();
        e.type = SPARK;
        e.x = x;
        e.y = y;
        e.vx = vx;
        e.vy = vy;
        e.drag = drag;
        e.life = life;
        e.radius = size;
        e.r = r;
        e.g = g;
        e.b = b;
        return e;
    }

    /** A drifting sprite puff: 4-frame sheet animation over `life`, billows as it fades. */
    public static Effect smoke(float x, float y, float vx, float vy, float r, float g, float b) {
        Effect e = new Effect();
        e.type = SMOKE;
        e.x = x;
        e.y = y;
        e.vx = vx;
        e.vy = vy;
        e.drag = 1.2f;
        e.life = 0.9f;
        e.radius = 32; // drawn size at birth; shrinks to 40% as it drifts off
        e.r = r;
        e.g = g;
        e.b = b;
        return e;
    }

    public static Effect ring(float x, float y, float radius, float r, float g, float b) {
        Effect e = new Effect();
        e.type = RING;
        e.x = x;
        e.y = y;
        e.life = 0.25f;
        e.radius = radius;
        e.r = r;
        e.g = g;
        e.b = b;
        return e;
    }

    public static Effect flash(float x, float y, float x2, float y2, float r, float g, float b) {
        Effect e = new Effect();
        e.type = FLASH;
        e.x = x;
        e.y = y;
        e.x2 = x2;
        e.y2 = y2;
        e.life = 0.09f;
        e.r = r;
        e.g = g;
        e.b = b;
        return e;
    }

    /** A flood: water that leaves the ship, floods out to `radius`, and then drains away.
     *  `life` covers travel AND drain, so a long life buys a slower wave, not a longer wait. */
    public static Effect surge(float x, float y, float radius, float life,
                               float bodyAlpha, float r, float g, float b) {
        Effect e = new Effect();
        e.type = SURGE;
        e.x = x;
        e.y = y;
        e.life = life;
        e.radius = radius;
        e.bodyAlpha = bodyAlpha;
        e.r = r;
        e.g = g;
        e.b = b;
        return e;
    }

    /** How far the water has travelled, 0..1. Eases hard out of the gate then coasts, which is
     *  what a spreading wave front actually does. Pure: the renderer reads it, so a test can too. */
    public float surgeReach() {
        float t = Math.min(1f, age / (life * travel));
        return 1f - (1f - t) * (1f - t);
    }

    public boolean line() {
        return type == FLASH;
    }

    public void update(float dt) {
        age += dt;
        if (vx != 0 || vy != 0) {
            float k = (float) Math.exp(-drag * dt);
            vx *= k;
            vy *= k;
            x += vx * dt;
            y += vy * dt;
            if (wobble != 0) {
                float len = (float) Math.sqrt(vx * vx + vy * vy);
                if (len > 1) { // a diminishing sideways sine — own phase per particle
                    float s = wobble * (float) Math.sin(age * 14 + phase) * (1 - age / life);
                    x += -vy / len * s * dt;
                    y += vx / len * s * dt;
                }
            }
        }
    }

    public boolean done() {
        return age >= life;
    }

    public float alpha() {
        return 1 - age / life;
    }
}
