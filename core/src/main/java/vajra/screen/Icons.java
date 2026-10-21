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

    // The bazaar's own icons: drawn large (96 px) and mip-mapped, so a coin or a gem in a menu is
    // a smooth object, not a blown-up sprite. The world and the HUD keep the pixel pickups above.
    private static final int ART = 96;
    private final Texture smoothCoinTex;
    private final TextureRegion smoothCoin;
    private final Texture[] smoothShardTex = new Texture[World.TATVA.length];
    private final TextureRegion[] smoothShard = new TextureRegion[smoothShardTex.length];

    public Icons() {
        smoothCoinTex = smooth(coinArt());
        smoothCoin = new TextureRegion(smoothCoinTex);
        for (int i = 0; i < smoothShardTex.length; i++) {
            float[] c = World.TATVA_RGB[i];
            smoothShardTex[i] = smooth(gemArt(new Color(c[0], c[1], c[2], 1f)));
            smoothShard[i] = new TextureRegion(smoothShardTex[i]);
        }
        coinTex = load("coins/coin_1.png", Color.GOLD, false);
        coin = new TextureRegion(coinTex);
        for (int i = 0; i < shardTex.length; i++) {
            float[] c = World.TATVA_RGB[i];
            shardTex[i] = load("shards/shard_" + (i + 1) + ".png", new Color(c[0], c[1], c[2], 1f), true);
            shard[i] = new TextureRegion(shardTex[i]);
        }
    }

    public TextureRegion coin() { return coin; }

    /** The menu coin: a gold kahapana with a punch-mark, smooth at any size. */
    public TextureRegion coinUi() { return smoothCoin; }

    /** The menu gem for one tatva element, smooth at any size. */
    public TextureRegion shardUi(int tatva) { return smoothShard[tatva]; }

    private static Texture smooth(Pixmap p) {
        Texture t = new Texture(p, true);
        t.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static void ring(Pixmap p, int cx, int cy, int r, int w, float[] col) {
        p.setColor(col[0], col[1], col[2], 1f);
        p.fillCircle(cx, cy, r);
        p.setColor(0, 0, 0, 0);
        p.setBlending(Pixmap.Blending.None);
        p.fillCircle(cx, cy, r - w);
        p.setBlending(Pixmap.Blending.SourceOver);
    }

    /** A punch-marked coin: a gold disc, a raised rim, and a square mark struck in the middle. */
    private static Pixmap coinArt() {
        Pixmap p = new Pixmap(ART, ART, Pixmap.Format.RGBA8888);
        int c = ART / 2;
        p.setColor(Puranic.COPPER[0] * 0.7f, Puranic.COPPER[1] * 0.7f, Puranic.COPPER[2] * 0.7f, 1f);
        p.fillCircle(c, c, 46);                                  // the shadowed edge
        p.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
        p.fillCircle(c, c, 43);
        ring(p, c, c, 40, 4, Puranic.COPPER);                     // the rim
        p.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
        p.fillCircle(c, c, 35);
        p.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        p.fillRectangle(c - 14, c - 14, 28, 28);                 // the punch-mark
        p.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
        p.fillRectangle(c - 9, c - 9, 18, 18);
        p.setColor(Puranic.OCHRE[0], Puranic.OCHRE[1], Puranic.OCHRE[2], 1f);
        p.fillCircle(c, c, 5);
        p.setColor(1f, 0.97f, 0.8f, 0.55f);
        p.fillCircle(c - 20, c + 20, 6);                         // a glint
        return p;
    }

    /** A cut gem: a rhombus in the element's colour, a lighter crown facet and a dark pavilion. */
    private static Pixmap gemArt(Color col) {
        Pixmap p = new Pixmap(ART, ART, Pixmap.Format.RGBA8888);
        int c = ART / 2;
        Color dark = new Color(col.r * 0.55f, col.g * 0.55f, col.b * 0.55f, 1f);
        Color light = new Color(Math.min(1f, col.r * 0.4f + 0.6f), Math.min(1f, col.g * 0.4f + 0.6f),
                Math.min(1f, col.b * 0.4f + 0.6f), 1f);
        p.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f); // the setting
        p.fillTriangle(c, c + 46, c + 36, c, c - 36, c);
        p.fillTriangle(c, c - 46, c + 36, c, c - 36, c);
        p.setColor(dark);
        p.fillTriangle(c, c + 40, c + 30, c, c - 30, c);
        p.setColor(col);
        p.fillTriangle(c, c - 40, c + 30, c, c - 30, c);
        p.setColor(light);
        p.fillTriangle(c, c - 40, c + 8, c - 4, c - 22, c - 4);
        p.setColor(col.r, col.g, col.b, 1f);
        p.fillTriangle(c, c - 4, c + 30, c, c, c + 40);
        return p;
    }

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
        smoothCoinTex.dispose();
        for (Texture t : smoothShardTex) t.dispose();
        coinTex.dispose();
        for (Texture t : shardTex) t.dispose();
    }
}
