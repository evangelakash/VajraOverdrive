package vajra.screen;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.Random;

/** Stars and parallax. Pure presentation: it owns nothing but its own motes. */
public class Backdrop {
    public float alpha = 1; // caller can dim the whole field
    private final float worldWidth, worldHeight;
    private final float[] starX;
    private final float[] starY;
    private final int[] starLayer;

    public Backdrop(float worldWidth, float worldHeight) {
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        Random rng = new Random(7);
        int far = 900, mid = 500, near = 240;
        int n = far + mid + near;
        starX = new float[n];
        starY = new float[n];
        starLayer = new int[n];
        for (int i = 0; i < n; i++) {
            starX[i] = rng.nextFloat() * worldWidth;
            starY[i] = rng.nextFloat() * worldHeight;
            starLayer[i] = i < far ? 0 : (i < far + mid ? 1 : 2);
        }
    }

    /** screenSpace: the home screen draws with no camera transform, so its camY is a rise to
     *  parallax with `-f` (stars fall as the hull climbs) instead of the world's `1 - f`. */
    public void draw(ShapeRenderer shapes, float camX, float camY, float l, float b, float vr,
                     float vt, boolean screenSpace) {
        for (int layer = 0; layer < 3; layer++) {
            float f = layer == 0 ? 0.3f : (layer == 1 ? 0.6f : 0.85f);
            float p = screenSpace ? -f : 1 - f;
            float s = layer == 0 ? 1 : (layer == 1 ? 2 : 3);
            if (layer == 0) shapes.setColor(0.5f, 0.5f, 0.55f, alpha);
            else if (layer == 1) shapes.setColor(0.8f, 0.8f, 0.85f, alpha);
            else shapes.setColor(1, 1, 1, alpha);
            for (int i = 0; i < starX.length; i++) {
                if (starLayer[i] != layer) continue;
                float dx = tilePos(starX[i] + camX * p, 0, worldWidth);
                if (dx < l - s || dx > vr) continue;
                float dy = tilePos(starY[i] + camY * p, 0, worldHeight);
                if (dy < b - s || dy > vt) continue;
                shapes.rect(dx, dy, s, s);
            }
        }
    }

    /** Positive modulo tile position. ponytail: one helper, no extra class. */
    static float tilePos(float base, float origin, float span) {
        float d = (base - origin) % span;
        if (d < 0) d += span;
        return origin + d;
    }
}
