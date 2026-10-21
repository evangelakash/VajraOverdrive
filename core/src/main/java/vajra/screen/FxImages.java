package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import vajra.world.Effect;

/** Laser beam, shockwave ring and engine thrust frames, cropped out of the Space Shooter pack in
 *  assets/ and whitened by tools/gen_fx.py so any weapon colour tints them. Thrust frames and
 *  the shockwave ring are nearest-filtered (the ring scales ~9x, the plume 2x) so both stay
 *  crisp; the beam keeps linear filtering because it is stretched the same way. Thrust frames are
 *  also trimmed to their ink so they centre exactly on the tail.
 *  Missing files become a drawn bar or ring, never a crash. */
public class FxImages {
    private static final float BEAM_H = 12;
    private final ArrayList<Texture> loaded = new ArrayList<>();
    private final TextureRegion beam, ring;
    private final TextureRegion[] thrust = new TextureRegion[16];
    private final TextureRegion[] smoke = split("effects/smoke-low-hull/low_hull.png", 16, 16);
    private final TextureRegion[] flame = split("effects/small-flame/pixilart-sprite.png", 16, 16);
    private final TextureRegion[] plant1 = split("atala/plants/plant_1.png", 16, 16);
    private final TextureRegion[] plant2 = split("atala/plants/plant_2.png", 16, 16);
    private final TextureRegion[] plant3 = split("atala/plants/plant_3.png", 16, 16);
    private final TextureRegion[] plant4 = split("atala/plants/plant_4.png", 16, 16);
    private final TextureRegion[][] plants = { plant1, plant2, plant3, plant4 };

    public FxImages() {
        beam = load(artPath("beam"), true);
        ring = load(artPath("ring"), false);
        // the shockwave scales up ~9x, so linear filtering turned it to mush
        ring.getTexture().setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        for (int lvl = 0; lvl < 4; lvl++)
            for (int frame = 0; frame < 4; frame++)
                thrust[lvl * 4 + frame] = loadOrNull(artPath("thrust-l" + lvl + "-f" + frame), true);
    }

    /** The pack crops keep their own names. */
    public static String artPath(String name) {
        return "atala/fx/" + name + ".png";
    }

    /** One of 4 power levels x 4 animation frames; null when the crops are missing. */
    public TextureRegion thrust(int level, int frame) {
        return thrust[level * 4 + frame];
    }

    private TextureRegion loadOrNull(String path, boolean nearest) {
        Texture t = ImageKit.texture(path, nearest);
        if (t == null) return null;
        loaded.add(t);
        return new TextureRegion(t);
    }

    /** Cut a fw x fh grid out of a sheet (row-major); null when the file is missing. */
    private TextureRegion[] split(String path, int fw, int fh) {
        TextureRegion sheet = loadOrNull(path, true);
        if (sheet == null) return null;
        Texture t = sheet.getTexture();
        TextureRegion[] out = new TextureRegion[(t.getWidth() / fw) * (t.getHeight() / fh)];
        int n = 0;
        for (int y = 0; y + fh <= t.getHeight(); y += fh)
            for (int x = 0; x + fw <= t.getWidth(); x += fw)
                out[n++] = new TextureRegion(t, x, y, fw, fh);
        return out;
    }

    /** Torch flame: base anchored at (x,y), rotated by `angle` so it always points off its
     *  wall (0 up, -90 right, 90 left, 180 down); 3-frame loop phase-offset per torch. */
    public void drawFlame(SpriteBatch batch, float x, float y, float angle, float time, int phase) {
        if (flame == null) return;
        int f = (int) (time * 8 + phase * 1.3f) % flame.length;
        batch.setColor(1, 1, 1, 1);
        batch.draw(flame[f], x - 16, y, 16, 0, 32, 32, 1, 1, angle);
    }

    /** Plant: base anchored at (x,y), rotated off its rock face (0 up, -90 right, 90 left,
     *  180 down), scaled by size (the scatter stands everything at 1x) while pulsing grow/shrink, swaying on a ping-pong
     *  over however many frames its sheet carries. phase is a fraction of one sway cycle. */
    public void drawPlant(SpriteBatch batch, float x, float y, int variant, float time, float phase,
            float angle, float size) {
        TextureRegion[] sheet = plants[variant];
        if (sheet == null) return;
        int f = plantFrame(sheet.length, time, phase);
        float s = size * (1f + 0.15f * (float) Math.sin(time * 1.4f + phase * 6.2831853f));
        batch.setColor(1, 1, 1, 1);
        batch.draw(sheet[f], x - 16, y, 16, 0, 32, 32, s, s, angle);
    }

    /** Ping-pong frame over an n-frame sheet: 0,1,…,n-1,n-2,…,1 — the wind never snaps back.
     *  One full cycle runs ~2 s whatever the length (n=3 walks the old 0,1,2,1 exactly). */
    static int plantFrame(int n, float time, float phase) {
        int steps = 2 * n - 2;
        int k = (int) (time * steps / 2 + phase * steps) % steps;
        return k < n ? k : steps - k;
    }

    private TextureRegion load(String path, boolean bar) {
        TextureRegion r = loadOrNull(path, false);
        if (r != null) return r;
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        if (bar) {
            for (int y = 0; y < 16; y++) {
                float d = Math.abs(y - 7.5f) / 8f;
                p.setColor(1, 1, 1, (1 - d) * (1 - d));
                for (int x = 0; x < 16; x++) p.drawPixel(x, y);
            }
        } else {
            p.setColor(1, 1, 1, 1);
            p.drawCircle(8, 8, 7);
        }
        Texture t = new Texture(p);
        p.dispose();
        loaded.add(t);
        return new TextureRegion(t);
    }

    /** RING: growing shockwave. FLASH: beam stretched from the muzzle to where it cut off.
     *  SMOKE: the 4-frame hull sheet, one frame per quarter of its life, billowing as it fades.
     *  SPARK is still a ShapeRenderer dot, so it is left alone here. */
    public void draw(SpriteBatch batch, Effect e) {
        if (e.type == Effect.SMOKE) {
            if (smoke == null) return;
            int f = Math.min(smoke.length - 1, (int) (e.age / e.life * smoke.length));
            float s = e.radius * (1 - 0.6f * e.age / e.life); // shrinks as it drifts away
            batch.setColor(e.r, e.g, e.b, e.alpha());
            batch.draw(smoke[f], e.x - s / 2, e.y - s / 2, s, s);
        } else if (e.type == Effect.RING) {
            float d = 2 * e.radius * (e.age / e.life);
            batch.setColor(e.r, e.g, e.b, e.alpha());
            batch.draw(ring, e.x - d / 2, e.y - d / 2, d, d);
        } else if (e.line()) {
            float dx = e.x2 - e.x, dy = e.y2 - e.y;
            batch.setColor(e.r, e.g, e.b, e.alpha());
            batch.draw(beam, e.x, e.y - BEAM_H / 2, 0, BEAM_H / 2,
                    (float) Math.hypot(dx, dy), BEAM_H, 1, 1,
                    (float) Math.toDegrees(Math.atan2(dy, dx)));
        }
        batch.setColor(1, 1, 1, 1);
    }

    public void dispose() {
        for (Texture t : loaded) t.dispose();
    }
}
