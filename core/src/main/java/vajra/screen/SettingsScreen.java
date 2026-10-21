package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.Settings;
import vajra.flow.Flow;
import vajra.flow.FlowState;

/** The settings screen, built to the bone: one opaque ink field, a SETTINGS title, a
 *  hairline, and four text rows (FULLSCREEN / SHAKE / POPUPS / BACK). No frames, no
 *  chrome. The selected row is gold and marked; A/D or clicking a row's left/right half
 *  adjusts, ESC leaves. Every change persists at once. */
public class SettingsScreen implements FlowState {
    static final String[] ROWS = {"FULLSCREEN", "SHAKE", "POPUPS", "BACK"};
    private static final int BACK = ROWS.length - 1;
    private static final float ROW_Y = 380;  // first row baseline: the menu zone every overlay shares
    private static final float PITCH = 56;   // row pitch
    private static final float HALF_W = 200; // row hit half-width
    private static final float HALF_H = 22;  // row hit half-height (matches the other menus)

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final Settings settings = vajra.profile.Profiles.active().settings;
    private final UiMenu menu;

    public SettingsScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.menu = new UiMenu(ROWS, ROW_Y, PITCH, HALF_W, HALF_H);
    }

    @Override
    public void update(float dt) {
        // nothing to advance: a still overlay
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            flow.pop();
            return true;
        }
        if (menu.keyDown(keycode)) return true;
        if (keycode == Input.Keys.A || keycode == Input.Keys.LEFT) {
            adjust(menu.selected(), -1);
            return true;
        }
        if (keycode == Input.Keys.D || keycode == Input.Keys.RIGHT) {
            adjust(menu.selected(), 1);
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activate();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        com.badlogic.gdx.math.Vector2 m = ui.at(sx, sy);
        int hit = menu.click(m);
        if (hit < 0) return false;
        if (hit == 0 || hit == BACK) activate();
        else adjust(hit, m.x < Template.COL ? -1 : 1); // left half down, right half up
        return true;
    }

    @Override
    public boolean mouseMoved(int sx, int sy) {
        menu.hover(ui.at(sx, sy));
        return false;
    }

    private void activate() {
        int sel = menu.selected();
        if (sel == 0) toggleFullscreen();
        else if (sel == BACK) flow.pop();
    }

    private void adjust(int row, int dir) {
        if (row == 0) {
            toggleFullscreen();
            return;
        }
        if (row == 1) settings.shake = clamp(settings.shake + 0.25f * dir, 0, 1);
        if (row == 2) settings.popupScale = clamp(settings.popupScale + 0.25f * dir, 0, 2);
        if (row == 1 || row == 2) vajra.profile.Profiles.save(); // each pilot keeps their own feel
    }

    /** Row cursor, wrapping. Package-private: the headless test pins the wrap. */
    static int move(int sel, int dir) {
        return UiMenu.wrap(sel, dir, ROWS.length);
    }

    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Same swap VajraGame's F11 bind performs, so the row and the key never disagree. */
    static void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(1920, 1080);
        else Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
    }

    /** What the row says: its label and, when it has one, its live value. */
    private String rowText(int i) {
        return menu.label(i) + (value(i).isEmpty() ? "" : "   " + value(i));
    }

    /** The row's live value. */
    private String value(int row) {
        if (row == 0) return Gdx.graphics.isFullscreen() ? "ON" : "OFF";
        if (row == 1) return Math.round(settings.shake * 100) + "%";
        if (row == 2) return Math.round(settings.popupScale * 100) + "%";
        return "";
    }

    @Override
    public void render() {
        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: its own screen, nothing bleeds through from below
        Ornament.frame(ui);
        Ornament.titleMarkers(ui, "SETTINGS", Template.TITLE_Y);
        Ornament.markers(ui, Template.COL, rowText(menu.selected()), menu.rowY(menu.selected()));
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        Template.title(ui, batch, "SETTINGS", Template.TITLE_Y, 1);
        for (int i = 0; i < menu.count(); i++)
            Template.menuRow(ui, batch, rowText(i), menu.selected(i), menu.rowY(i));
        Template.keys(ui, batch, Template.FOOT_Y, "W/S", "SELECT", "A/D or CLICK SIDES", "ADJUST", "ESC", "BACK");
        batch.end();
    }
}
