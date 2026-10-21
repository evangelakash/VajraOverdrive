package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.viewport.Viewport;
import vajra.enemy.Enemy;
import vajra.world.World;

/**
 * Atala is dim: a lightmap multiplies the finished world frame down to a faint ambient
 * colour, additive pools restore light around the ship and every enemy. HUD draws after
 * this and stays full-bright. ponytail: radial pools only — no wall line-of-sight yet.
 */
public class Lights {
    private static final float AMB = 0.15f;          // ambient floor: silhouettes, not black
    private final Texture glow;                      // one grayscale radial falloff, per light
    private FrameBuffer fbo;
    private TextureRegion map;

    public Lights() {
        glow = radial(128);
        glow.setFilter(TextureFilter.Linear, TextureFilter.Linear);
    }

    /** Dim the world in place. Call after the last world pass, before the shop dimmer/HUD.
     *  -Dvajra.nolights=true skips the whole pass: if the ship reappears, this pass is what
     *  was eating the post-lights layers. */
    public void render(SpriteBatch batch, OrthographicCamera uiCam, Viewport viewport,
                       World world, float l, float b, float viewW, float viewH) {
        if (Boolean.getBoolean("vajra.nolights")) return;
        int w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
        // a 0x0 window during resize makes the FBO throw and takes the whole pass down with
        // it; the multiply is dimming, not structural, so skip the frame's dim cleanly
        if (w <= 0 || h <= 0) return;
        if (fbo == null || fbo.getWidth() != w || fbo.getHeight() != h) {
            if (fbo != null) fbo.dispose();
            fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
            fbo.getColorBufferTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
            map = new TextureRegion(fbo.getColorBufferTexture());
            map.flip(false, true); // FrameBuffer content is upside down under SpriteBatch
        }

        float scale = uiCam.viewportWidth / viewW; // world units -> ui units (aspects match)

        // The world draws before this and the ship/player bullets draw after, all on the
        // caller's batch. The multiply below leaves the shared batch in GL_DST_COLOR/GL_ZERO
        // (and this pass owns an FBO that can fail to size on resize). Either one silently
        // eats every post-lights draw while the world still renders — so hand the batch back
        // exactly as we got it, or not at all.
        boolean drew = false;
        fbo.begin();
        try {
            Gdx.gl.glClearColor(AMB, AMB, AMB + 0.03f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            batch.setProjectionMatrix(uiCam.combined);
            batch.begin();
            drew = true;
            batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
            pool(batch, world.ship.x, world.ship.y, 340, 1f, 0.96f, 0.88f, l, b, scale);
            for (Enemy e : world.enemies) {
                if (e.dead || e.bossHidden) continue;
                float r = Math.max(e.w, e.h) * (e.boss ? 3f : 2.6f);
                pool(batch, e.x, e.y, r, 1f, e.boss ? 0.75f : 0.55f, e.boss ? 0.3f : 0.45f,
                        l, b, scale);
            }
            for (int i = 0; i < world.torches.length; i++) { // torch glow, mid-flame, flickering
                float[] t = world.torches[i];
                float a = (float) Math.toRadians(t[2]);
                float fl = 0.7f + 0.3f * (float) Math.sin(world.time() * 9 + i * 2.1f);
                pool(batch, t[0] - 16 * (float) Math.sin(a), t[1] + 16 * (float) Math.cos(a),
                        90, fl, 0.6f * fl, 0.22f * fl, l, b, scale);
            }
        } finally {
            if (drew) batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            fbo.end();
        }
        viewport.apply(); // fbo.end resets glViewport to the full window; restore the letterbox

        // the multiply re-enters the shared batch with GL_DST_COLOR/GL_ZERO; the ship and
        // player bullets draw right after on this same batch, so any bail here that skips
        // the restore leaves them invisible while the world still renders — restore unconditionally
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        try {
            batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO); // screen = screen * lightmap
            batch.draw(map, 0, 0, uiCam.viewportWidth, uiCam.viewportHeight);
        } finally {
            batch.end();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    /** One additive light pool; quads outside the viewport clip for free, no culling needed. */
    private void pool(SpriteBatch b, float wx, float wy, float r,
                      float cr, float cg, float cb, float l, float bl, float scale) {
        float size = 2 * r * scale;
        b.setColor(cr, cg, cb, 1);
        b.draw(glow, (wx - l) * scale - size / 2, (wy - bl) * scale - size / 2, size, size);
        b.setColor(1, 1, 1, 1);
    }

    /** The glow texture and the window-sized FBO: owned here, freed with the renderer. */
    public void dispose() {
        glow.dispose();
        if (fbo != null) fbo.dispose();
    }

    /** Grayscale radial falloff: RGB carries the light (additive blend ignores alpha). */
    private static Texture radial(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        float c = size / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x - c + 0.5f, y - c + 0.5f) / c;
                float t = Math.max(0f, 1 - d);
                float i = t * t * (3 - 2 * t); // smoothstep falloff
                pm.setColor(i, i, i, 1f);
                pm.drawPixel(x, y);
            }
        }
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }
}
