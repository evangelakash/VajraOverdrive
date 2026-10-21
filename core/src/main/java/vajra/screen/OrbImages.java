package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.world.Orb;

/** Loads the 8 orb frames. Missing files become gold placeholders, never a crash. */
public class OrbImages {
    public static final int FRAMES = 8;
    private final Texture[] tex = new Texture[FRAMES];
    private final TextureRegion[] reg = new TextureRegion[FRAMES];

    public OrbImages() {
        for (int i = 0; i < FRAMES; i++) {
            tex[i] = load("atala/energy_orb_1/eo_" + (i + 1) + ".png");
            reg[i] = new TextureRegion(tex[i]);
        }
    }

    private Texture load(String path) {
        Texture t = ImageKit.texture(path, true);
        if (t == null) {
            Pixmap p = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
            p.setColor(Color.GOLD);
            p.fillCircle(16, 16, 10);
            t = new Texture(p);
            p.dispose();
        }
        return t;
    }

    public void draw(SpriteBatch batch, Orb o) {
        TextureRegion r = reg[o.frameIndex(FRAMES)];
        if (r == null) return;
        float s = 32 * o.scale;
        batch.setColor(1, 1, 1, o.alpha());
        batch.draw(r, o.x - s / 2, o.y - s / 2, s / 2, s / 2, s, s, 1, 1, o.rotation);
        batch.setColor(1, 1, 1, 1);
    }

    public void dispose() {
        for (Texture t : tex) if (t != null) t.dispose();
    }
}
