package vajra.flow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The screen stack contract, headless: only the top updates and sees input, set() clears,
 *  popped states are finished exactly once, and global binds fire before any state. */
class FlowTest {
    private static class Stub implements FlowState {
        int updates, enters, exits, disposes, lastKey;

        @Override public void update(float dt) { updates++; }
        @Override public void render() {}
        @Override public void enter() { enters++; }
        @Override public void exit() { exits++; }
        @Override public void dispose() { disposes++; }
        @Override public boolean keyDown(int keycode) { lastKey = keycode; return true; }
    }

    @Test
    void onlyTheTopStateUpdatesAndSeesInput() {
        Flow flow = new Flow();
        Stub bottom = new Stub(), top = new Stub();
        flow.push(bottom);
        flow.push(top);
        flow.update(0.016f);
        assertEquals(1, top.updates, "the top updates");
        assertEquals(0, bottom.updates, "the one below is frozen");
        flow.keyDown(65);
        assertEquals(65, top.lastKey, "input lands on the top");
        assertEquals(0, bottom.lastKey, "the one below never sees it");
    }

    @Test
    void setClearsTheStackAndFinishesWhatItDrops() {
        Flow flow = new Flow();
        Stub old = new Stub();
        flow.push(old);
        Stub fresh = new Stub();
        flow.set(fresh);
        assertEquals(1, flow.depth(), "set leaves exactly one state");
        assertSame(fresh, flow.top());
        assertEquals(1, old.exits, "the dropped state exited");
        assertEquals(1, old.disposes, "the dropped state was disposed");
    }

    @Test
    void popFinishesTheStateExactlyOnce() {
        Flow flow = new Flow();
        Stub s = new Stub();
        flow.push(s);
        flow.pop();
        flow.pop(); // empty stack: nothing to do, no second dispose
        assertEquals(1, s.exits);
        assertEquals(1, s.disposes);
        assertEquals(0, flow.depth());
        assertNull(flow.top());
    }

    @Test
    void globalBindsFireBeforeTheTop() {
        Flow flow = new Flow();
        Stub s = new Stub();
        flow.push(s);
        final boolean[] ran = {false};
        flow.bind(292, () -> ran[0] = true); // F11: fullscreen works on any screen
        flow.keyDown(292);
        assertTrue(ran[0], "the bind ran");
        assertEquals(0, s.lastKey, "the state below never saw it");
    }
}
