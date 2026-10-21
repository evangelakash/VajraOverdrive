package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import vajra.enemy.Enemy;

/** Every idle_N a kind ships with, animated in sequence — auris carries 6 frames, the rest 2.
 *  Missing files become gold diamonds, never a crash. */
public class EnemyImages {
    private static final int MAX_FRAMES = 16;
    private static final float FPS = 4;
    private final Texture[][] tex = new Texture[Enemy.Kind.values().length][MAX_FRAMES];
    private final TextureRegion[][] reg = new TextureRegion[Enemy.Kind.values().length][MAX_FRAMES];
    private final int[] frames = new int[Enemy.Kind.values().length];

    public EnemyImages() {
        for (Enemy.Kind k : Enemy.Kind.values()) {
            String base = "atala/enemies/" + k.name().toLowerCase() + "/idle_";
            int f = 0;
            // frame 0 always loads (diamond if absent); the rest stop at the first gap
            while (f < MAX_FRAMES && (f == 0 || Gdx.files.internal(base + f + ".png").exists())) {
                tex[k.ordinal()][f] = load(base + f + ".png");
                reg[k.ordinal()][f] = new TextureRegion(tex[k.ordinal()][f]);
                f++;
            }
            frames[k.ordinal()] = f;
        }
    }

    private Texture load(String path) {
        Texture t = ImageKit.texture(path, true);
        if (t == null) {
            Pixmap p = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
            p.setColor(1, 0.84f, 0, 1);
            p.fillTriangle(16, 2, 30, 30, 2, 30);
            t = new Texture(p);
            p.dispose();
        }
        return t;
    }

    /** Which frame shows now: walks the whole cycle, staggered per unit by its phase. */
    public static int frame(float time, float phase, int frames) {
        return Math.floorMod((int) (time * FPS + phase), frames);
    }

    /** One idle frame as a bare region — a cutscene portrait. Frame 0 always loads. */
    public TextureRegion region(Enemy.Kind kind, int frame) {
        return reg[kind.ordinal()][Math.min(frame, frames[kind.ordinal()] - 1)];
    }

    public void draw(SpriteBatch batch, Enemy e, float time) {
        int f = frame(time, e.phase, frames[e.kind.ordinal()]);
        TextureRegion r = reg[e.kind.ordinal()][f];
        if (e.hitFlash > 0) batch.setColor(1f, 1f, 1f, 1);          // hit blink
        else if (e.elite) batch.setColor(1f, 0.84f, 0f, 1);
        else if (!e.active) batch.setColor(0.55f, 0.55f, 0.55f, 1);
        batch.draw(r, e.x - e.w / 2, e.y - e.h / 2, e.w, e.h);
        batch.setColor(1, 1, 1, 1);
    }

    public void dispose() {
        for (Texture[] row : tex) for (Texture t : row) if (t != null) t.dispose();
    }
}
