package vajra.world;

import java.util.Random;

/** Ambient energy orb. Hidden -> appears elsewhere -> loops frames + slow spin -> holds -> vanishes. Visual only. */
public class Orb {
    public float x, y;
    public float rotation;
    public float frame;
    public float age;
    public boolean visible;
    public float scale = 1f;

    private float hiddenFor;
    private float visibleFor = 2.3f;
    private static final float FRAME_FPS = 10f;
    private static final float SPIN_DEG = 25f;

    public Orb(float x, float y, float hiddenFor) {
        this.x = x;
        this.y = y;
        this.hiddenFor = hiddenFor;
        this.visible = false;
    }

    public void update(float dt, World world, Random rng) {
        if (!visible) {
            hiddenFor -= dt;
            if (hiddenFor <= 0) {
                float[] spot = world.randomOpenSpot(rng);
                x = spot[0];
                y = spot[1];
                visible = true;
                age = 0;
                frame = 0;
                float[] scales = { 1f, 1.5f, 2f };
                scale = scales[rng.nextInt(scales.length)];
            }
            return;
        }
        age += dt;
        frame += dt * FRAME_FPS;
        rotation = (rotation + dt * SPIN_DEG) % 360f;
        if (age >= visibleFor) {
            visible = false;
            hiddenFor = 1f + rng.nextFloat() * 3f;
        }
    }

    public int frameIndex(int count) {
        return ((int) frame) % count;
    }

    /** 0-1 fade in first 0.2s, out last 0.3s. */
    public float alpha() {
        float a = Math.min(1f, age / 0.2f);
        float left = visibleFor - age;
        if (left < 0.3f) a = Math.min(a, Math.max(0f, left / 0.3f));
        return a;
    }
}
