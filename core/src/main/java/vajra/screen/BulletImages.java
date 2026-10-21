package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import vajra.enemy.Enemy;
import vajra.weapon.Bullet;
import vajra.weapon.WeaponDef;

/** 16x16 bullet sprites, one per non-beam weapon, one per enemy gun (Enemy.GUN) plus the
 *  classic enemy fire (tools/gen_bullets.py). Missing files become glowing dots in the
 *  weapon colour, never a crash. */
public class BulletImages {
    private final Map<WeaponDef, TextureRegion> shots = new IdentityHashMap<>();
    private final TextureRegion foe;
    private final TextureRegion fallbackWhite;  // ADD: 1x1 white pixel fallback
    private final ArrayList<Texture> loaded = new ArrayList<>();

    public BulletImages() {
        for (WeaponDef d : WeaponDef.ALL) {
            if (d.beam) continue; // lasers draw as a line, they never spawn a sprite
            shots.put(d, load(artPath(d.name), d.r, d.g, d.b));
        }
        for (WeaponDef d : Enemy.GUN) // enemy signatures: per-kind art, colored-dot fallback
            if (d != null && !d.beam) shots.put(d, load(artPath(d.name), d.r, d.g, d.b));
        foe = load("atala/bullets/enemy.png", 1f, 0.42f, 0.3f);
        // white 1x1 pixel fallback for when sprite art is missing
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fillRectangle(0, 0, 1, 1);
        Texture t = new Texture(pm);
        pm.dispose();
        fallbackWhite = new TextureRegion(t);
        loaded.add(t);
    }

    /** "Single Bolt" -> "atala/bullets/single-bolt.png" — weapon names are the art key. */
    public static String artPath(String weaponName) {
        return "atala/bullets/" + weaponName.toLowerCase().replace(' ', '-') + ".png";
    }

    private TextureRegion load(String path, float r, float g, float b) {
        Texture t = ImageKit.texture(path, false);
        if (t == null) {
            Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            p.setColor(r, g, b, 1);
            p.fillCircle(8, 8, 6);
            t = new Texture(p);
            p.dispose();
        }
        loaded.add(t);
        return new TextureRegion(t);
    }

    /** Sized to the bullet's hit box, turned along its heading. */
    public void draw(SpriteBatch batch, Bullet b) {
        TextureRegion reg = shots.get(b.def);
        if (reg == null && b.hostile) reg = foe; // the classic sprite for undef'd enemy fire
        float w = b.radius * 2;
        float deg = (float) Math.toDegrees(Math.atan2(b.vy, b.vx));
        if (reg != null) {
            batch.draw(reg, b.x - w / 2, b.y - w / 2, w / 2, w / 2, w, w, 1, 1, deg);
        } else {
            // fallback: a small filled circle so the bullet is never invisible when art is missing
            batch.setColor(b.r, b.g, b.b, 1f);
            batch.draw(fallbackWhite, b.x - w / 2, b.y - w / 2, w / 2, w / 2, w, w, 1, 1, deg);
        }
    }

    public void dispose() {
        for (Texture t : loaded) t.dispose();
    }
}
