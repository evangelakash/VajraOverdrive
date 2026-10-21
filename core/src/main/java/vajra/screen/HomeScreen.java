package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.flow.Flow;
import vajra.flow.FlowState;
import vajra.update.Updater;

/** The title screen: a night street in a temple city, a valance of cloth with lamps and bells
 *  hung over it, and a pointed torana gateway whose doorway holds the seven menu rows. The name
 *  is set in type (Cinzel Decorative), never a bitmap: nothing on this screen is pixelated.
 *
 *  <p>Rows are PLAY / MULTIPLAYER / CONTROLS / PROFILE / SETTINGS / UPDATE / QUIT. ENTER or a
 *  click activates the selected row; the lit row is a palm-leaf plaque with a lotus either side.
 *  PLAY starts the run at once. UPDATE is the in-game swapper: it pulls the newest setup exe per
 *  vajra.update.Updater and lets the installer take over. */
public class HomeScreen implements FlowState {
    static final String[] MENU =
            {"PLAY", "MULTIPLAYER", "CONTROLS", "PROFILE", "SETTINGS", "UPDATE", "QUIT"};
    private static final float MENU_Y = 462;  // first row baseline: the seven rows hang in the doorway
    private static final float PITCH = 40;    // row pitch (rows abut at HALF_H 20, no dead zone)
    private static final float HALF_W = 190;  // row hit half-width
    private static final float HALF_H = 20;   // row hit half-height

    // the title block, top to bottom: the name, a lotus divider, the line under it, then the arch
    private static final float NAME_Y = 636, DIVIDER_Y = 590, SUB_Y = 574;
    // the torana: pavement, half-width, apex
    private static final float ARCH_BASE = 34, ARCH_HALF = 330, ARCH_TOP = 540;

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final UiMenu menu;
    private final Updater updater;

    public HomeScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.menu = new UiMenu(MENU, MENU_Y, PITCH, HALF_W, HALF_H);
        updater = new Updater();
        Template.setAccent(UiTheme.GOLD_R, UiTheme.GOLD_G, UiTheme.GOLD_B); // back to brand gold
    }

    // --- the vertical budget, exposed so a test can check it collides with nothing -----------

    static float menuY() { return MENU_Y; }
    static float pitch() { return PITCH; }
    static float halfH() { return HALF_H; }
    static float dividerY() { return DIVIDER_Y; }
    static float nameY() { return NAME_Y; }

    /** Menu cursor, wrapping. Package-private: the headless test pins the wrap. */
    static int move(int sel, int dir) {
        return UiMenu.wrap(sel, dir, MENU.length);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (menu.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activate();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (menu.click(ui.at(sx, sy)) >= 0) {
            activate();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int sx, int sy) {
        menu.hover(ui.at(sx, sy));
        return false;
    }

    private void activate() {
        int sel = menu.selected();
        if (sel == 0) flow.set(new GameScreen(batch, flow, ui));
        else if (sel == 1) flow.push(new NetScreen(flow, ui, batch));
        else if (sel == 2) flow.push(new ControlScreen(flow, ui, batch));
        else if (sel == 3) flow.push(new ProfileScreen(flow, ui, batch));
        else if (sel == 4) flow.push(new SettingsScreen(flow, ui, batch));
        else if (sel == 5) updater.begin();
        else Gdx.app.exit();
    }

    @Override
    public void update(float dt) {
        // nothing moves on this screen — the frame, divider and rows are all still
    }

    @Override
    public void render() {
        float cx = Ui.UI_W / 2f;

        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: the night street, its skyline and its stars
        Puranic.torana(ui, cx, ARCH_BASE, ARCH_HALF, ARCH_TOP, 1f);
        Puranic.vajra(ui, cx, 330, 2.6f, Puranic.GOLD, 0.07f);  // the weapon, faint, behind the menu
        Puranic.garland(ui, cx - 250, cx + 250, 164, 38, 22);  // strung across the doorway
        Puranic.canopy(ui, true); // the hung cloth, bells and lamps, over everything above
        Puranic.divider(ui, cx, DIVIDER_Y, 190, 0.9f);
        int sel = menu.selected();
        if (sel >= 0 && sel < menu.count())
            Ornament.markers(ui, cx, menu.label(sel), menu.rowY(sel));
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        drawName(cx);
        for (int i = 0; i < menu.count(); i++)
            Template.menuRow(ui, batch, menu.label(i), menu.selected(i), menu.rowY(i));
        if (updater.message().isEmpty()) // the hint row answers to the updater while it works
            Template.keys(ui, batch, UiTheme.FOOT_Y, "W/S", "SELECT", "ENTER / CLICK", "CONFIRM");
        else
            Template.sub(ui, batch, updater.message(), UiTheme.FOOT_Y, 0.85f);
        batch.end();
    }

    /** The name in Cinzel Decorative, larger than any other heading, with a soft shadow so it
     *  reads against the stars; and the line beneath it. */
    private void drawName(float cx) {
        com.badlogic.gdx.graphics.g2d.BitmapFont f = ui.title;
        float sx = f.getData().scaleX, sy = f.getData().scaleY;
        f.getData().setScale(sx * 1.7f, sy * 1.7f);
        f.setColor(0f, 0f, 0f, 0.55f);
        ui.centeredSpaced(batch, f, "VAJRA OVERDRIVE", NAME_Y - 3, 7f);
        f.setColor(Puranic.GOLD[0], Puranic.GOLD[1], Puranic.GOLD[2], 1f);
        ui.centeredSpaced(batch, f, "VAJRA OVERDRIVE", NAME_Y, 7f);
        f.setColor(1, 1, 1, 1);
        f.getData().setScale(sx, sy);
        ui.menu.setColor(Puranic.BRASS[0], Puranic.BRASS[1], Puranic.BRASS[2], 1f);
        ui.centered(batch, ui.menu, "THE CLIMB THROUGH ATALA", SUB_Y);
        ui.menu.setColor(1, 1, 1, 1);
    }

    @Override
    public void dispose() {
        // nothing owned: the title is set in type and drawn from shapes
    }
}