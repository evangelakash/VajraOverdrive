package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.flow.Flow;
import vajra.flow.FlowState;

/** The studio card: the SAHAH logo, centred on black, held, then faded out into the main menu.
 *
 *  <p>The beat: a beat of black while the window settles, the logo rising, a long hold to read
 *  it, then a slow outro. Anything the player presses skips it.
 *
 *  <p>The field is the menu's own ink rather than pure black, so the handover is seamless —
 *  {@link HomeScreen} draws an opaque field of exactly this colour, and if the splash ended on
 *  some other near-black the stack swap would step visibly. The two are a shade apart and read
 *  as black.
 *
 *  <p>NOTE on the art: the logo's wordmark and the vajra's outline are black, so on a black
 *  field the name does not show at all - only the flame and the grey vajra. That is what a
 *  black field does to this particular file. See the README.
 *
 *  <p>ESC resumes, Q quits, the mouse highlights and clicks. */
public class SplashScreen implements FlowState {
    // Public because the screenshot test samples frames at these marks, the way ShopUi
    // publishes its layout constants for the HUD screenshots.
    public static final float DELAY = 1.2f;     // black first: the window settles before the logo
    public static final float FADE_IN = 0.7f;    // logo 0 -> 1
    public static final float HOLD = 5.0f;       // long enough to read the name
    public static final float FADE_OUT = 2.0f;   // logo 1 -> 0
    public static final float TOTAL = DELAY + FADE_IN + HOLD + FADE_OUT;

    /** The logo's box on screen, 1.5x smaller than it was. The asset is 1959x1959 but only
     *  1267x1786 of it is ink, so the box is larger than the mark; centring the box centres
     *  the mark (they are concentric to within 8 px). */
    public static final float BOX = 373;

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final Texture logo;
    private float t;

    public SplashScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        // Asset paths are space-free and lower-kebab by contract (see docs/CODING_GUIDELINES.md):
        // libGDX's FileHandle backs Internal files with a classpath lookup that 404s on spaces
        // inside nested paths — the studio card is "brand/studio-logo.png" for exactly that.
        this.logo = new Texture(Gdx.files.internal("brand/studio-logo.png"));
        logo.setFilter(TextureFilter.Linear, TextureFilter.Linear);
    }

    // --- the curve, pure so it can be pinned headless -------------------------------------

    /** How solid the logo is, 0..1. Flat black through the delay, then in, hold, out. */
    public static float logoAlpha(float t) {
        if (t <= DELAY) return 0;
        if (t < DELAY + FADE_IN) return (t - DELAY) / FADE_IN;
        if (t < DELAY + FADE_IN + HOLD) return 1;
        if (t < TOTAL) return 1 - (t - DELAY - FADE_IN - HOLD) / FADE_OUT;
        return 0;
    }

    /** The splash is over once the outro has run out. */
    public static boolean done(float t) { return t >= TOTAL; }

    // --- FlowState ------------------------------------------------------------------------

    @Override
    public void update(float dt) {
        t += dt;
        if (done(t)) flow.set(new HomeScreen(flow, ui, batch)); // hands over to the menu
    }

    @Override
    public boolean keyDown(int keycode) {
        skip();
        return true;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        skip();
        return true;
    }

    /** Any input cuts the beat: a splash you cannot get past is the wrong kind of splash. */
    private void skip() { t = TOTAL; }

    @Override
    public void render() {
        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        ui.shapes.setColor(UiTheme.INK_R, UiTheme.INK_G, UiTheme.INK_B, 1f); // the studio card's ink field
        ui.shapes.rect(0, 0, Ui.UI_W, Ui.UI_H);
        ui.shapes.end();

        float a = logoAlpha(t);
        if (a > 0) {
            batch.setProjectionMatrix(ui.camera.combined);
            batch.begin();
            // one exit: the logo and the game name share the logo's own fade curve, in and out
            batch.setColor(1, 1, 1, a);
            batch.draw(logo, Ui.UI_W / 2f - BOX / 2, Ui.UI_H / 2f - BOX / 2, BOX, BOX);
            ui.brand.setColor(UiTheme.GOLD_R, UiTheme.GOLD_G, UiTheme.GOLD_B, a);
            ui.centeredSpaced(batch, ui.brand, "THE BURNING SHELL",
                    Ui.UI_H / 2f - BOX / 2f - 22f, 3f);
            ui.brand.setColor(1, 1, 1, 1);
            batch.setColor(1, 1, 1, 1);
            batch.end();
        }
    }

    @Override
    public void dispose() { logo.dispose(); }
}