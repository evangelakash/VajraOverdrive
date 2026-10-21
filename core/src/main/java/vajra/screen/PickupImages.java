package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.world.Pickup;
import vajra.world.World;

/** Loads every pickup: 5 coin variants, health, the 5 Pancha Tatva shards and the Vajrayudha
 *  band that just dropped. Missing files become shape-and-colour placeholders, never a crash. */
public class PickupImages {
    private static final float SHARD_SCALE = 2.5f; // 16 px art reads at 40
    private final Texture[] coinTex = new Texture[Pickup.VARIANTS];
    private final TextureRegion[] coinReg = new TextureRegion[Pickup.VARIANTS];
    private final Texture[] shardTex = new Texture[World.TATVA.length];
    private final TextureRegion[] shardReg = new TextureRegion[World.TATVA.length];
    private final VajraImages vj = new VajraImages(); // the boss piece: its band of the blade
    private final Texture healthTex;
    private final TextureRegion healthReg;

    public PickupImages() {
        for (int i = 0; i < Pickup.VARIANTS; i++) {
            coinTex[i] = load("coins/coin_" + (i + 1) + ".png", Color.GOLD, 'o');
            coinReg[i] = new TextureRegion(coinTex[i]);
        }
        for (int i = 0; i < World.TATVA.length; i++) {
            Color c = new Color(World.TATVA_RGB[i][0], World.TATVA_RGB[i][1], World.TATVA_RGB[i][2], 1f);
            shardTex[i] = load("shards/shard_" + (i + 1) + ".png", c, 'd');
            shardReg[i] = new TextureRegion(shardTex[i]);
        }
        healthTex = load("health_drop/health.png", Color.LIME, '+');
        healthReg = new TextureRegion(healthTex);
    }

    /** placeholder shapes: o disc, d diamond-ish dot, s square, + cross. */
    private Texture load(String path, Color ph, char shape) {
        Texture t = ImageKit.texture(path, true);
        if (t == null) {
            Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            p.setColor(ph);
            switch (shape) {
                case 'o': p.fillCircle(8, 8, 6); break;
                case 'd': p.fillCircle(8, 8, 4); p.fillRectangle(7, 3, 2, 10); p.fillRectangle(3, 7, 10, 2); break;
                case 's': p.fillRectangle(3, 3, 10, 10); break;
                default:  p.fillRectangle(5, 1, 6, 14); p.fillRectangle(1, 5, 14, 6); break;
            }
            t = new Texture(p);
            p.dispose();
        }
        return t;
    }

    /** Native sizes, 2.5x for shards (they must read off the rock) and 2x for health and the
     *  Vajrayudha band — with a gentle bob: it floats. */
    public void draw(SpriteBatch batch, Pickup p, float time) {
        float bob = (float) Math.sin(time * 3 + p.phase) * 1.5f;
        switch (p.type) {
            case Pickup.SHARD: {
                batch.setColor(1, 1, 1, 1);
                float s = shardReg[p.variant].getRegionWidth() * SHARD_SCALE;
                batch.draw(shardReg[p.variant], p.x - s / 2, p.y - s / 2 + bob, s, s);
                break;
            }
            case Pickup.PIECE: // its band of the blade, 2x so the slice still reads
                batch.setColor(1, 1, 1, 1);
                vj.drawBand(batch, p.variant, p.x, p.y + bob);
                break;
            case Pickup.HEALTH:
                batch.setColor(1, 1, 1, 1);
                batch.draw(healthReg, p.x - 16, p.y - 16 + bob, 32, 32);
                break;
            default:
                batch.setColor(1, 1, 1, 1);
                batch.draw(coinReg[p.variant], p.x - 8, p.y - 8 + bob);
                break;
        }
    }

    public void dispose() {
        for (Texture t : coinTex) if (t != null) t.dispose();
        for (Texture t : shardTex) if (t != null) t.dispose();
        vj.dispose();
        if (healthTex != null) healthTex.dispose();
    }
}
