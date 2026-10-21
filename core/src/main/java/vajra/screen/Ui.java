package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;

/** The shared presentation kit every state and overlay borrows: the 1280x720 UI camera,
 *  its viewport, the single ShapeRenderer, the font set, the menu icon set and the text /
 *  blend / mouse-coordinate helpers. Built and disposed by VajraGame — states never own it. */
public final class Ui {
    public static final float UI_W = UiTheme.SCREEN_W, UI_H = UiTheme.SCREEN_H;

    public final OrthographicCamera camera; // UI space, y up: overlays and HUD draw here
    /** The UI camera's viewport. Every state draws through it, so the Flow applies it once a
     *  frame — without that, a UI-only screen has no glViewport of its own and keeps whatever
     *  the last world render left behind. Built in the constructor: it needs `camera`. */
    private final FitViewport viewport;
    public final ShapeRenderer shapes;      // one renderer, begin/end always paired
    public final BitmapFont hud;    // Courbe Sans: HUD stats, prompts, numbers (FONT_BODY)
    public final BitmapFont score;  // Rajdhani SemiBold 30: the run's score, top-centre
    public final BitmapFont menu;   // Courbe Sans: subtitles under a heading (FONT_SUBHEAD)
    public final BitmapFont title;  // Rajdhani SemiBold, wide-tracked: screen headings
    public final BitmapFont brand;  // Courbe Sans at LOCKUP_SIZE: the splash's game-name line
    public final BitmapFont caption; // Courbe Sans 11: notification captions and tiny labels
    public final BitmapFont ping;   // the OS's own Arial 12: the crew list's plain RTT readout
    public final Icons icons;       // coin + shard faces for menus, prices and the forge meter
    private final GlyphLayout layout = new GlyphLayout();
    private final Vector3 unproj = new Vector3();
    private final Vector2 mouse = new Vector2();

    public Ui() {
        camera = new OrthographicCamera(UI_W, UI_H);
        camera.position.set(UI_W / 2, UI_H / 2, 0);
        camera.update();
        viewport = new FitViewport(UI_W, UI_H, camera);
        shapes = new ShapeRenderer();
        hud = font(UiTheme.FONT_BODY, UiTheme.BODY_SIZE); // techno HUD face — the sci-fi pass
        score = font(UiTheme.FONT_NUMERAL, UiTheme.SCORE_SIZE); // clean condensed numerals
        menu = font(UiTheme.FONT_SUBHEAD, UiTheme.SUBHEAD_SIZE);
        title = font(UiTheme.FONT_TITLE, UiTheme.TITLE_SIZE);
        brand = font(UiTheme.FONT_BODY, UiTheme.LOCKUP_SIZE);
        caption = font(UiTheme.FONT_BODY, UiTheme.CAPTION_SIZE);
        ping = fontPlain(12);
        icons = new Icons();
    }

    /** Match the UI camera to the live framebuffer and set glViewport. Called once a frame by
     *  the Flow for every state, and again by GameScreen for its own world camera. */
    public void applyViewport() {
        viewport.update(Math.max(1, Gdx.graphics.getWidth()), Math.max(1, Gdx.graphics.getHeight()));
    }

    /** Screen pixels (as Flow delivers them) -> UI units. The returned vector is reused:
     *  copy anything you keep. */
    public Vector2 at(int sx, int sy) {
        unproj.set(sx, sy, 0);
        camera.unproject(unproj);
        mouse.set(unproj.x, unproj.y);
        return mouse;
    }

    /** Centred on screen, baseline at y (UI units). */
    public void centered(SpriteBatch batch, BitmapFont font, String text, float y) {
        layout.setText(font, text);
        font.draw(batch, text, UI_W / 2 - layout.width / 2, y);
    }

    /** Right-aligned on x, baseline at y (UI units). */
    public void right(SpriteBatch batch, BitmapFont font, String text, float x, float y) {
        layout.setText(font, text);
        font.draw(batch, text, x - layout.width, y);
    }

    /** Width of `text` in `font` via the shared layout — read it immediately. */
    public float measure(BitmapFont font, String text) {
        layout.setText(font, text);
        return layout.width;
    }

    /** Centred on screen with tracking between glyphs — the wide sci-fi caps heading.
     *  One glyph at a time, so nothing is allocated per frame. */
    public void centeredSpaced(SpriteBatch batch, BitmapFont font, CharSequence text, float y,
                               float spacing) {
        BitmapFont.BitmapFontData d = font.getData();
        float total = -spacing;
        for (int i = 0; i < text.length(); i++) {
            BitmapFont.Glyph g = d.getGlyph(text.charAt(i));
            if (g != null) total += g.xadvance + spacing;
        }
        float x = UI_W / 2 - total / 2;
        for (int i = 0; i < text.length(); i++) {
            BitmapFont.Glyph g = d.getGlyph(text.charAt(i));
            font.draw(batch, text, x, y, i, i + 1, 0, Align.left, false);
            if (g != null) x += g.xadvance + spacing;
        }
    }

    /** ShapeRenderer never touches GL blend state and SpriteBatch.end() disables it, so every
     *  shape pass has to re-enable blending itself or alpha renders opaque. */
    public void beginShapes(ShapeRenderer.ShapeType type) {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(type);
    }

    /** One TTF from assets/fonts into a bitmap font; the generator is single-use, so it dies here. */
    private static BitmapFont font(String file, int size) {
        return generate(Gdx.files.internal("fonts/" + file), size,
                " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
                        + "abcdefghijklmnopqrstuvwxyz{|}~—–°×"); // full ASCII + em dash + degree + the combo ×
    }

    /** The crew list's plain face: the OS's own Arial for the RTT numbers — consciously
     *  unlike the sci-fi HUD faces, so ping reads as a readout, not as flavour. Windows and
     *  macOS both ship Arial; anything else falls back to the game's sans body face. */
    private static BitmapFont fontPlain(int size) {
        String os = System.getProperty("os.name", "").toLowerCase();
        String[] paths = os.contains("win")
                ? new String[] { "C:/Windows/Fonts/arial.ttf" }
                : new String[] { "/System/Library/Fonts/Supplemental/Arial.ttf",
                                 "/Library/Fonts/Arial.ttf" };
        for (String p : paths) {
            com.badlogic.gdx.files.FileHandle h = Gdx.files.absolute(p);
            if (h.exists()) return generate(h, size, "0123456789-");
        }
        return font(UiTheme.FONT_BODY, size);
    }

    private static BitmapFont generate(com.badlogic.gdx.files.FileHandle file, int size, String chars) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(file);
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.characters = chars;
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        return f;
    }

    public void dispose() {
        shapes.dispose();
        hud.dispose();
        score.dispose();
        menu.dispose();
        title.dispose();
        caption.dispose();
        ping.dispose();
        icons.dispose();
    }
}
