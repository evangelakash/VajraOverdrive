package vajra.screen;

import vajra.audio.Audio;
import vajra.audio.Sfx;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;

/** List-menu component: labels stacked on fixed rows with one cursor. It owns cursor state and
 *  keyboard/mouse selection only; screens draw the rows so each keeps its own frame art. */
public final class UiMenu {
    private final String[] labels;
    private final float rowTop, pitch, x0, x1, halfHeight;
    private int sel;

    public UiMenu(String[] labels, float rowTop, float pitch, float halfWidth, float halfHeight) {
        this.labels = labels;
        this.rowTop = rowTop;
        this.pitch = pitch;
        this.halfHeight = halfHeight;
        float cx = Template.COL;
        this.x0 = cx - halfWidth;
        this.x1 = cx + halfWidth;
    }

    public float rowY(int i) { return rowTop - i * pitch; }
    public int count() { return labels.length; }
    public int selected() { return sel; }
    public String label(int i) { return labels[i]; }
    public boolean selected(int i) { return i == sel; }

    /** Keyboard cursor: W/Up/Down wrap; returns true when it consumed the key. */
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.W || keycode == Input.Keys.UP) return move(-1);
        if (keycode == Input.Keys.S || keycode == Input.Keys.DOWN) return move(1);
        return false;
    }

    private boolean move(int delta) {
        sel = wrap(selected(), delta, count());
        Audio.ui(Sfx.UI_MOVE);
        return true;
    }

    public static int wrap(int sel, int delta, int count) {
        return (sel + delta + count) % count;
    }

    /** Row index under the cursor, or -1. Does not change the cursor. */
    public int hit(Vector2 m) {
        for (int i = 0; i < count(); i++) {
            float y = rowY(i);
            if (Template.box(m, x0, y - halfHeight, x1, y + halfHeight)) return i;
        }
        return -1;
    }

    /** Hover moves the cursor without acting. */
    public boolean hover(Vector2 m) {
        int i = hit(m);
        if (i >= 0 && i != sel) Audio.ui(Sfx.UI_MOVE); // only a NEW leaf ticks, not every frame over it
        if (i >= 0) sel = i;
        return i >= 0;
    }

    /** Click moves the cursor and returns the row activated, or -1 outside every row. */
    public int click(Vector2 m) {
        int i = hit(m);
        if (i >= 0) sel = i;
        return i;
    }
}
