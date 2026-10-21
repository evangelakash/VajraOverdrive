package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.SaveGame;
import vajra.flow.Flow;
import vajra.flow.FlowState;
import vajra.world.World;

/** The pause overlay: one opaque ink field over the frozen run, a PAUSED title, and three text
 *  rows (RESUME / SETTINGS / QUIT TO TITLE). Nothing else — no run state. The run is frozen
 *  behind the ink, so what it was doing is still there to read on resume, and every field this
 *  used to print is already on the HUD or in the hangar.
 *
 *  <p>It wears the same temple-geometry ornament as the title screen (hairline frame, diamond
 *  divider, diamond markers on the selected row) but none of its motion: the run is frozen, so
 *  the screen is too.
 *
 *  <p>ESC resumes, Q quits, the mouse highlights and clicks. */
public class PauseScreen implements FlowState {
    static final String[] ROWS = {"RESUME", "SETTINGS", "QUIT TO TITLE"};
    private static final float ROW_Y = 380;   // first row baseline
    private static final float PITCH = 56;    // row pitch
    private static final float HALF_W = 200;  // row hit half-width
    private static final float HALF_H = 22;   // row hit half-height (matches the other menus)

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final World world;
    private final UiMenu menu;

    public PauseScreen(Flow flow, Ui ui, SpriteBatch batch, World world) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.world = world;
        this.menu = new UiMenu(ROWS, ROW_Y, PITCH, HALF_W, HALF_H);
    }

    @Override
    public void enter() {
        // the talk key is not polled while the pause is up (the run beneath is frozen), so
        // the channel must be told to stop — otherwise the microphone stays open and the
        // room hears you over the pause menu
        if (vajra.net.Net.session != null) vajra.net.Net.session.voice.push(false);
    }

    @Override
    public void update(float dt) {
        // nothing to advance: the run beneath is frozen and the menu is still
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            flow.pop(); // back to the run
            return true;
        }
        if (menu.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activate();
            return true;
        }
        if (keycode == Input.Keys.Q) {
            quit();
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
        if (sel == 0) flow.pop();
        else if (sel == 1) flow.push(new SettingsScreen(flow, ui, batch)); // the run stays frozen
        else quit();
    }

    private void quit() {
        // Abandoning a run is still a run: its score, kills and seconds fold into the pilot's
        // book before dropping it, the same exit the natural end takes.
        vajra.profile.Profiles.endRun(world);
        if (vajra.net.Net.session != null) {
            // A race flight abandoned counts too — report it, then the pause hands the
            // pilot back to the lobby (a root of its own), where the roster kept flying.
            // A LOST session comes back too: that is where "HOST LOST" is written, and
            // where LEAVE finally closes the socket and the microphone.
            vajra.net.Net.session.report(world.score.points);
            vajra.net.Net.session.backToLobby();
            flow.set(new LobbyScreen(flow, ui, batch));
            return;
        }
        flow.set(new HomeScreen(flow, ui, batch)); // abandon the run
    }

    @Override
    public void render() {
        float cx = Ui.UI_W / 2f;

        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: the run beneath is hidden, not shown
        Ornament.frame(ui);
        Ornament.titleMarkers(ui, "PAUSED", Template.TITLE_Y);
        Ornament.markers(ui, cx, menu.label(menu.selected()), menu.rowY(menu.selected()));
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        Template.title(ui, batch, "PAUSED", Template.TITLE_Y, 1);
        for (int i = 0; i < menu.count(); i++)
            Template.menuRow(ui, batch, menu.label(i), menu.selected(i), menu.rowY(i));
        Template.keys(ui, batch, Template.FOOT_Y, "W/S", "SELECT", "ENTER", "CONFIRM", "ESC", "RESUME", "Q", "QUIT");
        batch.end();
    }
}