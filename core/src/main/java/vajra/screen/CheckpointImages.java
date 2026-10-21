package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.world.Checkpoint;

/** The three checkpoint crops from assets/checkpoint/, drawn as one concentric stack: the pink
 *  collector behind, the gold globe over it, the gold frame on top. Both orbs share one transform
 *  — same breath scale, same spin — so they read identically sized. Missing files become drawn
 *  placeholders, never a crash. */
public class CheckpointImages {
    public static final String DIR = "checkpoint/";
    private static final int SPOUT = 12;
    private final TextureRegion holder, orb1, orb2;

    public CheckpointImages() {
        holder = load("checkpoint_holder", 'h');
        orb1 = load("checkpoint_orb1", 'g');
        orb2 = load("checkpoint_orb2", 'o');
    }

    public static String artPath(String name) {
        return DIR + name + ".png";
    }

    private TextureRegion load(String name, char fallback) {
        Texture t = ImageKit.texture(artPath(name), true);
        if (t == null) {
            Pixmap p = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
            if (fallback == 'o') {
                p.setColor(Color.PINK);
                p.fillCircle(16, 16, 7);
            } else {
                p.setColor(Color.GOLD);
                if (fallback == 'h') p.drawRectangle(2, 2, 28, 28);
                else p.fillCircle(16, 16, 12);
            }
            t = new Texture(p);
            p.dispose();
        }
        return new TextureRegion(t);
    }

    /** All three crops at 2× (64×64), concentric; bottom to top: collector, globe, frame. Both
     *  orbs take the exact same transform — one breath scale, one spin angle — so the pink disc
     *  is always the same size as the globe as it turns in place. */
    public void draw(SpriteBatch batch, Checkpoint c, float time) {
        float a = c.alpha();
        float breath = 1f + 0.15f * (float) Math.sin(time * 2f);
        float spin = time * 45 % 360;
        float s = 64, h = 32;
        batch.setColor(1, 1, 1, a);
        if (!c.collected) {
            batch.draw(orb2, c.x - h, c.y - h, h, h, s, s, breath, breath, spin);
        }
        batch.draw(orb1, c.x - h, c.y - h, h, h, s, s, breath, breath, spin);
        batch.draw(holder, c.x - h, c.y - h, s, s);
        batch.setColor(1, 1, 1, 1);
    }

    /** Tiny glow motes streaming left and right out of the centre, fading in and out — pure
     *  functions of time. Drawn BEHIND the stack, and only while it is unspent. */
    public void drawParticles(ShapeRenderer shapes, Checkpoint c, float time) {
        if (c.collected) return;
        for (int i = 0; i < SPOUT; i++) {
            float side = i % 2 == 0 ? -1 : 1;
            float u = (time / 1.2f + i * 0.148f) % 1f;
            float px = c.x + side * u * 70;
            float py = c.y + ((i % 3) - 1) * u * 4; // a hair of spread, not a ruled line
            shapes.setColor(1f, 0.45f, 0.72f, (float) Math.sin(u * Math.PI) * 0.8f);
            shapes.circle(px, py, 0.6f + (1 - u) * 0.6f, 6);
        }
    }

    public void dispose() {
        if (holder != null) holder.getTexture().dispose();
        if (orb1 != null) orb1.getTexture().dispose();
        if (orb2 != null) orb2.getTexture().dispose();
    }
}
