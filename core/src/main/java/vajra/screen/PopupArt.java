package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.HashMap;

/** Pre-rendered Devanagari popup phrases (assets/popup_art/*.png — white glyphs, shaped by
 *  DirectWrite in tools/gen_popup_art.ps1 because FreeType cannot shape Devanagari).
 *  A missing file just means that popup falls back to its English line, never a crash. */
public final class PopupArt {
    public static final String[] KEYS = {
            "dvitiya", "tritiya", "chaturth", "pancham", "shanti",
            "bala", "pratishtha", "pravesh", "vajra", "taptam"
    };
    private final HashMap<String, TextureRegion> art = new HashMap<>();
    private final ArrayList<Texture> loaded = new ArrayList<>();

    public PopupArt() {
        for (String k : KEYS) {
            Texture t = ImageKit.texture("popup_art/" + k + ".png", true);
            if (t != null) {
                loaded.add(t); // kit textures need manual tracking for dispose
                art.put(k, new TextureRegion(t));
            }
        }
    }

    public TextureRegion get(String key) {
        return key == null ? null : art.get(key);
    }

    public void dispose() {
        for (Texture t : loaded) t.dispose();
    }
}
