package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.world.World;

/** The menu-side pickup faces: one coin and the Pancha Tatva shards (assets/coins,
 *  assets/shards) — the same art the world drops, read at icon size in the hangar's wallet,
 *  price lines and forge meter. A missing file becomes a flat placeholder, never a crash.
 *  Owned by Ui, like the fonts. */
public final class Icons {
    private final Texture coinTex;
    private final TextureRegion coin;
    private final Texture[] shardTex = new Texture[World.TATVA.length];
    private final TextureRegion[] shard = new TextureRegion[shardTex.length];

    public Icons() {
        coinTex = load("coins/coin_1.png", Color.GOLD, false);
        coin = new TextureRegion(coinTex);
        for (int i = 0; i < shardTex.length; i++) {
            float[] c = World.TATVA_RGB[i];
            shardTex[i] = load("shards/shard_" + (i + 1) + ".png", new Color(c[0], c[1], c[2], 1f), true);
            shard[i] = new TextureRegion(shardTex[i]);
        }
    }

    public TextureRegion coin() { return coin; }

    /** The shard of one tatva element (World.TATVA index). */
    public TextureRegion shard(int tatva) { return shard[tatva]; }

    /** Placeholder shapes: a disc for the coin, a diamond in the element's colour for a shard. */
    private static Texture load(String path, Color ph, boolean diamond) {
        Texture t = ImageKit.texture(path, true);
        if (t == null) {
            Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            p.setColor(ph);
            if (diamond) {
                p.fillCircle(8, 8, 4);
                p.fillRectangle(7, 3, 2, 10);
                p.fillRectangle(3, 7, 10, 2);
            } else {
                p.fillCircle(8, 8, 6);
            }
            t = new Texture(p);
            p.dispose();
        }
        return t;
    }

    public void dispose() {
        coinTex.dispose();
        for (Texture t : shardTex) t.dispose();
    }
}
