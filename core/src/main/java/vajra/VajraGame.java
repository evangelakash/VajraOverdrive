package vajra;

import vajra.audio.Audio;
import vajra.audio.SfxPlayer;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import vajra.flow.Flow;
import vajra.net.Net;
import vajra.screen.SplashScreen;
import vajra.screen.Ui;

/** Boots the Flow screen stack (the one Screen libGDX gets) and owns the three things
 *  every state borrows: the batch, the Ui kit and the Flow itself. States are pushed,
 *  popped and replaced on the Flow; nothing else reaches the input or the screen.
 *  The window settings ride on Flow's global binds, so F11/F12 work on every screen. */
public class VajraGame extends Game {
    private SpriteBatch batch;
    private Ui ui;
    private Flow flow;
    private int winW = 1920, winH = 1080; // the windowed pair F12 swaps between (HD is home)

    @Override
    public void create() {
        batch = new SpriteBatch();
        ui = new Ui();
        Audio.use(new SfxPlayer()); // every sound from here on; silence if there is no device
        flow = new Flow();
        flow.ui(ui); // so it can keep the UI viewport current for states with none of their own
        flow.bind(Input.Keys.F11, this::toggleFullscreen);
        flow.bind(Input.Keys.F12, this::cycleWindowed);
        Gdx.input.setInputProcessor(flow); // every key, mouse and wheel event lands on the stack
        setScreen(flow);
        // FULLSCREEN ALWAYS: the game opens on the display's own mode, not the 1920x1080 window.
        // Do it before the first render so the very first frame - the studio card - is already
        // the size the player will see the game at. F11 still drops back to windowed.
        Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        flow.set(new SplashScreen(flow, ui, batch)); // then the main menu
    }

    /** F11: fullscreen on the current display, or back to the windowed size. */
    private void toggleFullscreen() {
        if (Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(winW, winH);
        else Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
    }

    /** F12: swap the window between 1280x720 and 1920x1080 (leaves fullscreen for either). */
    private void cycleWindowed() {
        winW = winW == 1280 ? 1920 : 1280;
        winH = winW == 1280 ? 720 : 1080;
        Gdx.graphics.setWindowedMode(winW, winH);
    }

    @Override
    public void dispose() {
        Audio.bus().stopLoops();
        Net.close(); // first: a quitting guest says BYE while its socket still lives
        if (flow != null) flow.dispose(); // pops (and disposes) every state on the stack
        if (ui != null) ui.dispose();
        if (batch != null) batch.dispose();
        super.dispose();
    }
}
