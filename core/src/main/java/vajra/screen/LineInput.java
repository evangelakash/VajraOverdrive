package vajra.screen;

import java.util.function.Consumer;

import com.badlogic.gdx.Input;

/** One line of typed text, edited inside the game. The platform dialog can't do
 *  this: the LWJGL3 input dialog is a documented no-op in gdx 1.14.2
 *  (DefaultLwjgl3Input: "FIXME getTextInput does nothing"), so JOIN A RUN never
 *  opened anything. The game owns the field instead: keyTyped carries the
 *  characters through the OS layout (lower/upper, @ and : included), ENTER
 *  confirms, BACKSPACE deletes, ESC abandons — and while the line is open it eats
 *  every key, so the screens hand their input here first. */
public final class LineInput {

    private boolean active;
    private String title = "", hint = "";
    private StringBuffer buf = new StringBuffer();
    private Consumer<String> confirm = t -> {};

    public void open(String title, String hint, Consumer<String> confirm) {
        this.title = title;
        this.hint = hint;
        this.confirm = confirm;
        buf.setLength(0);
        active = true;
    }

    public void close() { active = false; }
    public boolean isActive() { return active; }
    public String text() { return buf.toString(); }
    public String title() { return title; }
    public String hint() { return hint; }

    /** Printable characters only; control bytes are sound, the key handler owns them. */
    public boolean typed(char c) {
        if (!active) return false;
        if (c >= 32 && c != 127) buf.append(c);
        return true;
    }

    /** Enter confirms and closes FIRST (the callback may push a screen), backspace
     *  and forward-delete erase, escape abandons; every other named key is eaten. */
    public boolean key(int keycode) {
        if (!active) return false;
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
            String done = buf.toString();
            active = false;
            confirm.accept(done);
            return true;
        }
        if (keycode == Input.Keys.BACKSPACE || keycode == Input.Keys.FORWARD_DEL) {
            if (buf.length() > 0) buf.setLength(buf.length() - 1);
            return true;
        }
        if (keycode == Input.Keys.ESCAPE) close();
        return true;
    }
}
