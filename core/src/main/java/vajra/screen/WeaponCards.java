package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import java.util.ArrayList;
import java.util.Locale;
import vajra.weapon.WeaponDef;

/** 16x16 weapon face cards, one per pattern (assets/weapon_display_images): the fitted one
 *  sits in the HUD's bottom-left plate. Missing files become a dim disc in the weapon colour,
 *  never a crash. */
public final class WeaponCards {
    private final TextureRegion[] card = new TextureRegion[WeaponDef.ALL.length];
    private final ArrayList<Texture> loaded = new ArrayList<>();

    public WeaponCards() {
        for (int i = 0; i < WeaponDef.ALL.length; i++) {
            WeaponDef d = WeaponDef.ALL[i];
            card[i] = load("weapon_display_images/" + fileKey(d.name) + ".png", d.r, d.g, d.b);
        }
    }

    /** "Single Bolt" -> "single_bolt"; the art's own spellings win where they differ. */
    public static String fileKey(String name) {
        switch (name) {
            case "Laser": return "lazer";
            case "Charge Shot": return "charged_shot";
            case "Tracking Missiles": return "tracking_missile";
            default:
                return name.toLowerCase(Locale.ROOT).replace(' ', '_');
        }
    }

    public TextureRegion get(int weaponIndex) {
        return card[weaponIndex];
    }

    private TextureRegion load(String path, float r, float g, float b) {
        Texture t = ImageKit.texture(path, true);
        if (t == null) {
            Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
            p.setColor(r, g, b, 0.35f);
            p.fillCircle(8, 8, 7);
            t = new Texture(p);
            p.dispose();
        }
        loaded.add(t);
        return new TextureRegion(t);
    }

    public void dispose() {
        for (Texture t : loaded) t.dispose();
    }
}
