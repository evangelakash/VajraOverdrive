package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.flow.Flow;
import vajra.flow.FlowState;

/** THE CONTROL SCREEN — the full key and mouse reference for the run.
 *
 *  <p>Pushed from the home menu (and from pause). Lists every flight, hangar and system
 *  binding so the player never has to alt-tab out to remember them. ESC or a click returns
 *  to the screen that pushed it. */
public class ControlScreen implements FlowState {
    private static final float SUB_Y = Template.TITLE_Y - 40; // the subtitle, right under the heading
    private static final int PER_COL = 9;
    private static final float TOP = 466, ROW_H = 44;          // the first row's centre and the pitch
    private static final float[] COL_X = { 52, 664 };          // the two columns
    private static final float CHIP_W = 160, CHIP_H = 26, DESC_DX = 176, DESC_W = 388;

    private static final String[][] ROWS = {
            {"W A S D / arrows", "Thrust — screen relative. W is always up, D always right. Never turns the ship."},
            {"Q / E", "Turn the nose by hand — Q counter-clockwise, E clockwise."},
            {"SPACE / LMB", "Fire. SPACE fires along the nose; LMB also swings the nose onto the cursor (twin-stick)."},
            {"SHIFT", "Boost while thrusting — drains the energy meter."},
            {"C", "Dash in the pushed direction (along the nose if no key held). 0.4 s i-frames — shots pass through."},
            {"F", "Release the Akasha Astra — spends one forged charge: 20 homing cubes through rock, mends the hull, returns a life."},
            {"G / RMB", "Shield — drains while held. Cost scales with what actually lands."},
            {"V / B", "Next / previous owned weapon."},
            {"1–7", "Switch to that hull — a hull this run has not bought is a no-op."},
            {"Q / Z", "Cycle to the previous / next hull you own."},
            {"H / ENTER", "Hangar — docking is automatic; no key opens it."},
            {"ESC", "Close the hangar / pause the run / quit to title."},
            {"TAB", "In the hangar: next section (HULL / PATTERN / ASTRA)."},
            {"Wheel", "Zoom in / out, 0.5×–2.0× in 0.1 steps."},
            {"F11", "Toggle fullscreen / back to windowed (the game opens fullscreen)."},
            {"F12", "Swap the window between 1280×720 and 1920×1080."},
            {"T", "Debug: take 15 hull damage on demand."},
            {"R", "New run after game over."},
    };

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;

    public ControlScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
    }

    @Override
    public void update(float dt) {
        // a still reference page
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.ENTER) {
            flow.pop();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        flow.pop();
        return true;
    }

    @Override
    public void render() {
        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: the night street
        Ornament.frame(ui);
        Ornament.titleMarkers(ui, "CONTROLS", Template.TITLE_Y);
        for (int i = 0; i < ROWS.length; i++) { // each key sits on its own palm-leaf chip
            float x = COL_X[i / PER_COL], y = TOP - (i % PER_COL) * ROW_H;
            float w = Math.max(CHIP_W, ui.measure(ui.body, ROWS[i][0]) + 40);
            Puranic.leaf(ui, x, y - CHIP_H / 2f, w, CHIP_H, Puranic.BARK, 0.95f, false);
            Puranic.c(ui, Puranic.COPPER, 0.25f);
            ui.shapes.rect(x + DESC_DX - 8, y - ROW_H / 2f + 5, 1, ROW_H - 10);
        }
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        Template.title(ui, batch, "CONTROLS", Template.TITLE_Y, 1f);
        Template.sub(ui, batch, "Every binding the run uses", SUB_Y, 1f);
        for (int i = 0; i < ROWS.length; i++) {
            float x = COL_X[i / PER_COL], y = TOP - (i % PER_COL) * ROW_H;
            float w = Math.max(CHIP_W, ui.measure(ui.body, ROWS[i][0]) + 40);
            ui.body.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
            ui.body.draw(batch, ROWS[i][0], x + (w - ui.measure(ui.body, ROWS[i][0])) / 2f, y + 7);
            ui.small.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 0.85f);
            layout.setText(ui.small, ROWS[i][1], ui.small.getColor(), DESC_W, com.badlogic.gdx.utils.Align.left, true);
            ui.small.draw(batch, layout, x + DESC_DX, y + layout.height / 2f);
        }
        ui.body.setColor(1, 1, 1, 1);
        ui.small.setColor(1, 1, 1, 1);
        Template.keys(ui, batch, UiTheme.FOOT_Y, "ESC / CLICK", "RETURN");
        batch.end();
    }

    private final com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();

    @Override
    public void dispose() {}
}
