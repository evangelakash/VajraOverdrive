package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.world.World;

/** The SHOP sign + docking bay from assets/shop/, drawn at 4× (128×128) over open ground like
 *  the checkpoints: shop_undocked while parked out, doc1..doc6 filling the bay lights as it
 *  docks (0.1 s per frame), doc6 held once docked. A missing file becomes a drawn
 *  placeholder, never a crash. */
public class ShopImages {
    public static final String DIR = "shop/";
    private final TextureRegion undocked;
    private final TextureRegion[] doc = new TextureRegion[6];

    public ShopImages() {
        undocked = load("shop_undocked");
        for (int i = 0; i < 6; i++) doc[i] = load("shop_doc" + (i + 1));
    }

    private TextureRegion load(String name) {
        String path = DIR + name + ".png";
        Texture t = ImageKit.texture(path, true);
        if (t == null) {
            Pixmap p = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
            p.setColor(Color.GOLD);
            p.drawRectangle(2, 2, 28, 28);
            t = new Texture(p);
            p.dispose();
        }
        return new TextureRegion(t);
    }

    public void draw(SpriteBatch batch, float x, float y, int state, float timer) {
        TextureRegion r = undocked;
        if (state == World.DOCKING) r = doc[Math.min(5, (int) (timer * 10))]; // 0.1 s/frame
        else if (state == World.DOCKED || state == World.SITTING) r = doc[5]; // final frame
        batch.draw(r, x - 64, y - 64, 128, 128); // 4× the 32px crop
    }

    public void dispose() {
        undocked.getTexture().dispose();
        for (TextureRegion d : doc) d.getTexture().dispose();
    }
}
