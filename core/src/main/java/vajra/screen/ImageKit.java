package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.Texture.TextureWrap;

/** THE one texture loader every image class delegates to. It owns the exists-check, the
 *  filter and the swallow of missing or unreadable files: a missing crop is a returned
 *  {@code null} — the caller draws its own placeholder — never a crash, and never another
 *  copy of this try/catch in a fourteenth class. */
public final class ImageKit {
    private ImageKit() {}

    /** The texture at {@code path}, or null when it is missing. Nearest keeps pixel crops
     *  crisp at 2-4x; Linear smooths art that is scaled or blends (bullets, text). */
    public static Texture texture(String path, boolean nearest) {
        try {
            if (Gdx.files.internal(path).exists()) {
                Texture t = new Texture(Gdx.files.internal(path));
                t.setFilter(nearest ? TextureFilter.Nearest : TextureFilter.Linear,
                        nearest ? TextureFilter.Nearest : TextureFilter.Linear);
                return t;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** A texture that scrolls: linear + tiling wrap (the nebula is the one user so far). */
    public static Texture tile(String path) {
        Texture t = texture(path, false);
        if (t != null) t.setWrap(TextureWrap.Repeat, TextureWrap.Repeat);
        return t;
    }
}
