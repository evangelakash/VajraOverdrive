package vajra.flow;

/** One layer of the game: a hard state (Title, Play) or an overlay pushed on top of one
 *  (Pause, Shop, GameOver). The Flow stack owns the contract:
 *
 *  - only the TOP state receives update(dt) and every input event;
 *  - every state renders, bottom of the stack first, so overlays draw over the play field;
 *  - a state that leaves the stack is finished: exit() then dispose(), exactly once;
 *  - mutate the stack only from update() or an input handler, never from render().
 *
 *  Lifecycle and resize default to no-ops and input defaults to "not handled" — override
 *  only what a given state needs. */
public interface FlowState {
    default void enter() {}
    default void exit() {}

    /** Advance this state by dt seconds. Only ever called on the top of the stack. */
    void update(float dt);

    /** Draw this state. Called for every state each frame, bottom of the stack first. */
    void render();

    default void resize(int width, int height) {}

    /** The state is leaving the stack for good — release anything it owns. */
    default void dispose() {}

    default boolean keyDown(int keycode) { return false; }
    default boolean keyUp(int keycode) { return false; }
    default boolean keyTyped(char character) { return false; }
    default boolean touchDown(int x, int y, int pointer, int button) { return false; }
    default boolean touchUp(int x, int y, int pointer, int button) { return false; }
    default boolean touchDragged(int x, int y, int pointer) { return false; }
    default boolean touchCancelled(int x, int y, int pointer, int button) { return false; }
    default boolean mouseMoved(int x, int y) { return false; }
    default boolean scrolled(float amountX, float amountY) { return false; }
}
