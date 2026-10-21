package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** One tileable fluid-nebula texture, scrolled slowly with faint parallax. Missing file = black, never a crash. */
public class NebulaImages {
    private final Texture tex;
    private final TextureRegion reg;

    public NebulaImages() {
        Texture t = ImageKit.tile("atala/space/fluid_nebula.png");
        if (t == null) {
            Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGB888);
            p.setColor(0, 0, 0, 1);
            p.fill();
            t = new Texture(p);
            p.dispose();
            t.setFilter(TextureFilter.Linear, TextureFilter.Linear);
            t.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        }
        tex = t;
        reg = new TextureRegion(t);
    }

    public void draw(SpriteBatch batch, float l, float b, float w, float h, float camX, float camY, float t) {
        float world = 2048;
        float u = (camX * 0.05f + t * 6) / world;
        float v = (camY * 0.05f + t * 4) / world;
        reg.setRegion(u, v, u + w / world, v + h / world);
        batch.draw(reg, l, b, w, h);
    }

    public void dispose() {
        tex.dispose();
    }
}
