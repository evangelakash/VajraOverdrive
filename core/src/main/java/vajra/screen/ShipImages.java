package vajra.screen;

import vajra.ship.Ship;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.ship.ShipDef;

/** Player ship sprites: vajra_ships/mk_<n>/ship_<v>.png, one folder per ship, three
 *  variations per ship (tools/gen_ships.py). A missing file means the polygon fallback. */
public class ShipImages {
    private static final int VARIATIONS = 3;
    private final TextureRegion[][] reg = new TextureRegion[ShipDef.ALL.length][VARIATIONS];

    public ShipImages() {
        for (ShipDef d : ShipDef.ALL) {
            for (int v = 0; v < VARIATIONS; v++) {
                reg[d.id][v] = load(artPath(d.id, v + 1));
            }
        }
    }

    /** Ship ids are 0-based, folders are 1-based: mk_1 .. mk_7. */
    public static String artPath(int shipId, int variation) {
        return "vajra_ships/mk_" + (shipId + 1) + "/ship_" + variation + ".png";
    }

    private TextureRegion load(String path) {
        Texture t = ImageKit.texture(path, true);
        return t == null ? null : new TextureRegion(t); // caller falls back to the polygon hull
    }

    /** variation is 1..3 (the ship's upgrade tier). Null when the file is missing. */
    public TextureRegion get(int shipId, int variation) {
        if (shipId < 0 || shipId >= reg.length || variation < 1 || variation > VARIATIONS) return null;
        return reg[shipId][variation - 1];
    }

    public void dispose() {
        for (TextureRegion[] row : reg) {
            for (TextureRegion r : row) {
                if (r != null) r.getTexture().dispose();
            }
        }
    }
}
