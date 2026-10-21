package vajra.flow;

import vajra.audio.Audio;
import vajra.audio.Sfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.GL20;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** The screen stack. VajraGame hands one Flow to setScreen() and Gdx.input once, and from
 *  then on every screen, overlay and menu is just a FlowState on this stack — push, pop,
 *  replace, done. Contracts (detailed on FlowState): only the top state updates and gets
 *  input; all states render bottom-first; popped states are finished (exit + dispose).
 *
 *  <p>It also holds the shared UI kit and applies its viewport once a frame, before anything
 *  paints. Only GameScreen ever had a viewport of its own, so a UI-only state (the studio card,
 *  the title, pause, settings) kept whatever glViewport the last world render left behind —
 *  which after the boot-time switch to fullscreen was stale, and left a black strip down one
 *  edge until PLAY pushed a screen that happened to fix it itself. See Ui.applyViewport.
 *
 *  <p>render(delta) is split into update() + clear() + paint() so the stack logic itself runs
 *  headless in FlowTest — only clear() needs a GL context. */
public class Flow implements Screen, InputProcessor {
    private final ArrayList<FlowState> stack = new ArrayList<>();
    private final Map<Integer, Runnable> binds = new HashMap<>(); // keys that work on any state
    private vajra.screen.Ui ui; // optional: headless tests render nothing and leave it null

    /** Hand the Flow the shared UI kit so it can keep that viewport current. */
    public Flow ui(vajra.screen.Ui ui) { this.ui = ui; return this; }

    /** Show an overlay above the current top (or a new state above nothing). */
    public void push(FlowState s) {
        stack.add(s);
        s.enter();
    }

    /** Close the top state: it is finished, so exit() then dispose(), exactly once. */
    public void pop() {
        Audio.ui(Sfx.UI_BACK); // leaving a screen
        if (stack.isEmpty()) return;
        FlowState s = stack.remove(stack.size() - 1);
        s.exit();
        s.dispose();
    }

    /** Hard transition: clear the whole stack, then push (title -> play, play -> title). */
    public void set(FlowState s) {
        while (!stack.isEmpty()) pop();
        push(s);
    }

    public FlowState top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public boolean isTop(FlowState s) {
        return top() == s;
    }

    public int depth() {
        return stack.size();
    }

    /** Keys handled before any state sees them: fullscreen now, mute later. */
    public void bind(int keycode, Runnable action) {
        binds.put(keycode, action);
    }

    /** Advance the top state only — overlays freeze what lies beneath them. */
    public void update(float dt) {
        vajra.net.Net.pump(); // the session's heartbeats outrank the stack: a paused race must not be swept
        FlowState t = top();
        if (t != null) t.update(dt);
    }

    /** Draw the whole stack, bottom of the stack first. */
    public void paint() {
        // The UI viewport goes down FIRST and every frame. GameScreen re-applies its own world
        // viewport inside render(); the UI-only states have none and would otherwise inherit a
        // stale glViewport.
        if (ui != null) ui.applyViewport();
        for (int i = 0; i < stack.size(); i++) stack.get(i).render();
    }

    // --- Screen ---------------------------------------------------------------

    @Override
    public void render(float delta) {
        update(Math.min(delta, 1 / 30f)); // one clamp, one place: alt-tab can't explode the sim
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        paint();
    }

    @Override
    public void resize(int width, int height) {
        for (FlowState s : new ArrayList<>(stack)) s.resize(width, height);
    }

    @Override
    public void dispose() {
        while (!stack.isEmpty()) pop();
    }

    @Override public void show() {}
    @Override public void pause() {}
    @Override public void resume() {}
    @Override public void hide() {}

    // --- InputProcessor: global binds first, then the top state only -----------

    @Override
    public boolean keyDown(int keycode) {
        Runnable a = binds.get(keycode);
        if (a != null) {
            a.run();
            return true;
        }
        FlowState t = top();
        return t != null && t.keyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        FlowState t = top();
        return t != null && t.keyUp(keycode);
    }

    @Override
    public boolean keyTyped(char character) {
        FlowState t = top();
        return t != null && t.keyTyped(character);
    }

    @Override
    public boolean touchDown(int x, int y, int pointer, int button) {
        FlowState t = top();
        return t != null && t.touchDown(x, y, pointer, button);
    }

    @Override
    public boolean touchUp(int x, int y, int pointer, int button) {
        FlowState t = top();
        return t != null && t.touchUp(x, y, pointer, button);
    }

    @Override
    public boolean touchDragged(int x, int y, int pointer) {
        FlowState t = top();
        return t != null && t.touchDragged(x, y, pointer);
    }

    @Override
    public boolean touchCancelled(int x, int y, int pointer, int button) {
        FlowState t = top();
        return t != null && t.touchCancelled(x, y, pointer, button);
    }

    @Override
    public boolean mouseMoved(int x, int y) {
        FlowState t = top();
        return t != null && t.mouseMoved(x, y);
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        FlowState t = top();
        return t != null && t.scrolled(amountX, amountY);
    }
}
