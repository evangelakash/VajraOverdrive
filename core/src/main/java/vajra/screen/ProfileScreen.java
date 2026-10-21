package vajra.screen;

import vajra.audio.Audio;
import vajra.audio.Sfx;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.Locale;
import vajra.flow.Flow;
import vajra.flow.FlowState;
import vajra.profile.Profile;
import vajra.profile.Profiles;

/** The pilot's page: a retro-terminal callsign plate (type your name straight into it, max 10
 *  characters) above a ruled campaign ledger, with the action rows beneath.
 *
 *  <p>On desktop the name is typed inline, one cell per character. On Android/iOS there is no
 *  physical keyboard, so RENAME falls back to the platform's own text dialog. Names are
 *  sanitized on the way in (Profiles.rename) and every change persists at once. */
public class ProfileScreen implements FlowState {
    static final String[] ROWS = {"SWITCH PILOT", "RENAME", "NEW PILOT", "BACK"};
    static final int NAME_LEN = 10;                    // hard cap for a pilot name
    private static final float ROW_Y = 250;
    private static final float PITCH = 44;
    private static final float HALF_W = 200, HALF_H = 22;

    // callsign plate
    private static final float PLATE_W = 430, PLATE_Y = 436, PLATE_H = 70;
    private static final float CELL_W = 34, CELL_H = 34, CELL_GAP = 6, CELL_Y = 462;

    // ledger panel
    private static final float LEDGER_W = 360, LEDGER_TOP = 414, LEDGER_PITCH = 22, LEDGER_PAD = 8;
    private static final String[] LABELS = {"BEST", "RUNS", "KILLS", "POINTS", "TIME"};

    // palette
    private static final float[] CYAN = Puranic.BRONZE;           // the heading bronze
    private static final float[] CYAN_DIM = Puranic.BARK;          // bark, dim
    private static final float[] PANEL = Puranic.PLATE;

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final UiMenu menu;
    private final GlyphLayout layout = new GlyphLayout();

    private boolean editing;
    private final StringBuilder buffer = new StringBuilder();
    private float clock;                               // drives the cursor blink

    public ProfileScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.menu = new UiMenu(ROWS, ROW_Y, PITCH, HALF_W, HALF_H);
    }

    @Override
    public void update(float dt) {
        clock += dt;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean keyDown(int keycode) {
        if (editing) return typing(keycode);           // while editing, the keyboard is the name's

        if (keycode == Input.Keys.ESCAPE) {
            flow.pop();
            return true;
        }
        if (menu.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activate();
            return true;
        }
        return false;
    }

    private boolean typing(int k) {
        if (k == Input.Keys.ESCAPE) { editing = false; return true; }
        if (k == Input.Keys.ENTER || k == Input.Keys.NUMPAD_ENTER) { commit(); return true; }
        if (k == Input.Keys.BACKSPACE || k == Input.Keys.FORWARD_DEL) {
            if (buffer.length() > 0) buffer.setLength(buffer.length() - 1);
            return true;
        }
        char c = charFor(k);
        if (c != 0 && buffer.length() < NAME_LEN) buffer.append(c);
        return true;
    }

    /** Letters, digits, space and dash; the brand face is all-caps anyway. */
    private static char charFor(int k) {
        if (k >= Input.Keys.A && k <= Input.Keys.Z) return (char) ('A' + (k - Input.Keys.A));
        if (k >= Input.Keys.NUM_0 && k <= Input.Keys.NUM_9) return (char) ('0' + (k - Input.Keys.NUM_0));
        if (k >= Input.Keys.NUMPAD_0 && k <= Input.Keys.NUMPAD_9) return (char) ('0' + (k - Input.Keys.NUMPAD_0));
        if (k == Input.Keys.SPACE) return ' ';
        if (k == Input.Keys.MINUS) return '-';
        return 0;
    }

    private void commit() {
        String name = buffer.toString().trim();
        if (!name.isEmpty()) Profiles.rename(name);
        editing = false;
    }

    private void beginEdit() {
        if (isTouchDevice()) {                          // no hardware keyboard: use the OS dialog
            Gdx.input.getTextInput(new Input.TextInputListener() {
                @Override public void input(String text) {
                    String t = text == null ? "" : text.trim();
                    if (t.length() > NAME_LEN) t = t.substring(0, NAME_LEN);
                    if (!t.isEmpty()) Profiles.rename(t);
                }
                @Override public void canceled() {}
            }, "PILOT NAME", Profiles.active().name, "up to " + NAME_LEN + " characters");
            return;
        }
        buffer.setLength(0);
        String cur = Profiles.active().name;
        buffer.append(cur.length() > NAME_LEN ? cur.substring(0, NAME_LEN) : cur);
        editing = true;
        clock = 0;
    }

    private static boolean isTouchDevice() {
        Application.ApplicationType t = Gdx.app.getType();
        return t == Application.ApplicationType.Android || t == Application.ApplicationType.iOS;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (editing) { commit(); return true; }
        if (menu.click(ui.at(sx, sy)) >= 0) {
            activate();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int sx, int sy) {
        if (!editing) menu.hover(ui.at(sx, sy));
        return false;
    }

    private void activate() {
        Audio.ui(Sfx.UI_SELECT);
        int sel = menu.selected();
        if (sel == 0) Profiles.select(1);               // wrap to the next pilot on the roster
        else if (sel == 1) beginEdit();
        else if (sel == 2) { Profiles.create(null); beginEdit(); } // new pilot, then name them
        else flow.pop();
    }

    // ---------------------------------------------------------------- data

    private String value(int row) {
        Profile.Stats s = Profiles.active().campaign;
        switch (row) {
            case 0: return String.format(Locale.US, "%,d", s.best);
            case 1: return String.valueOf(s.runs);
            case 2: return String.valueOf(s.kills);
            case 3: return String.format(Locale.US, "%,d", s.points);
            default: return clock(s.seconds);
        }
    }

    /** Career seconds as h:mm:ss (or m:ss before the first hour). */
    static String clock(float t) {
        int s = (int) t, h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%d:%02d", m, sec);
    }

    // ---------------------------------------------------------------- drawing helpers

    private void col(float[] c, float a) { ui.shapes.setColor(c[0], c[1], c[2], a); }

    private void rect(float x, float y, float w, float h) { ui.shapes.rect(x, y, w, h); }

    private void frame(float x, float y, float w, float h, float t) {
        rect(x, y, w, t);
        rect(x, y + h - t, w, t);
        rect(x, y, t, h);
        rect(x + w - t, y, t, h);
    }

    /** Corner brackets: the sci-fi "targeting reticle" look. */
    private void brackets(float x, float y, float w, float h, float len, float t) {
        rect(x, y, len, t);                 rect(x, y, t, len);
        rect(x + w - len, y, len, t);       rect(x + w - t, y, t, len);
        rect(x, y + h - t, len, t);         rect(x, y + h - len, t, len);
        rect(x + w - len, y + h - t, len, t); rect(x + w - t, y + h - len, t, len);
    }

    private void text(BitmapFont f, String s, float x, float y, float[] c, float a) {
        f.setColor(c[0], c[1], c[2], a);
        f.draw(batch, s, x, y);
        f.setColor(1, 1, 1, 1);
    }

    private void textCentered(BitmapFont f, String s, float cx, float cy, float[] c, float a) {
        layout.setText(f, s);
        text(f, s, cx - layout.width / 2f, cy + layout.height / 2f, c, a);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        final float cx = Template.COL;
        final String name = editing ? buffer.toString() : Profiles.active().name;
        final boolean blink = ((int) (clock * 2.2f)) % 2 == 0;

        final float plateX = cx - PLATE_W / 2f;
        final float cellsW = NAME_LEN * CELL_W + (NAME_LEN - 1) * CELL_GAP;
        final float cellsX = cx - cellsW / 2f;

        final float ledgerX = cx - LEDGER_W / 2f;
        final float ledgerH = LABELS.length * LEDGER_PITCH + LEDGER_PAD * 2;
        final float ledgerY = LEDGER_TOP - ledgerH;

        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: its own screen, nothing bleeds through from below
        Ornament.frame(ui);
        Ornament.titleMarkers(ui, "PROFILE", Template.TITLE_Y);
        if (!editing)
            Ornament.markers(ui, Template.COL, menu.label(menu.selected()), menu.rowY(menu.selected()));

        // --- callsign plate
        Puranic.plate(ui, plateX, PLATE_Y, PLATE_W, PLATE_H, 0.95f, editing ? 1f : 0.75f);

        for (int i = 0; i < NAME_LEN; i++) {
            float x = cellsX + i * (CELL_W + CELL_GAP);
            boolean filled = i < name.length();
            boolean cursor = editing && i == name.length();
            col(CYAN_DIM, filled ? 0.55f : 0.25f);
            rect(x, CELL_Y, CELL_W, CELL_H);
            if (cursor && blink) {
                col(CYAN, 0.85f);
                rect(x, CELL_Y, CELL_W, CELL_H);
            }
            col(CYAN, filled || cursor ? 0.9f : 0.35f);
            rect(x, CELL_Y, CELL_W, 2);                    // underline of each slot
        }

        // --- ledger panel: ruled rows
        Puranic.plate(ui, ledgerX, ledgerY, LEDGER_W, ledgerH, 0.95f, 0.75f);
        col(CYAN_DIM, 0.8f);
        for (int i = 1; i < LABELS.length; i++)
            rect(ledgerX + 14, LEDGER_TOP - LEDGER_PAD - i * LEDGER_PITCH + 3, LEDGER_W - 28, 1);
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        Template.title(ui, batch, "PROFILE", Template.TITLE_Y, 1);

        // plate caption + counter
        float capY = PLATE_Y + 18;
        text(ui.small, editing ? "TYPE CALLSIGN" : "CALLSIGN", plateX + 16, capY, CYAN, 0.95f);
        String count = name.length() + "/" + NAME_LEN;
        layout.setText(ui.small, count);
        text(ui.small, count, plateX + PLATE_W - 16 - layout.width, capY, CYAN, 0.95f);

        // the name, one glyph per cell
        for (int i = 0; i < name.length() && i < NAME_LEN; i++) {
            float x = cellsX + i * (CELL_W + CELL_GAP) + CELL_W / 2f;
            boolean onCursor = false;
            textCentered(ui.head, String.valueOf(name.charAt(i)), x, CELL_Y + CELL_H / 2f,
                    new float[]{UiTheme.GOLD_R, UiTheme.GOLD_G, UiTheme.GOLD_B}, onCursor ? 0f : 1f);
        }

        // ledger text: label left, value right
        for (int i = 0; i < LABELS.length; i++) {
            float y = LEDGER_TOP - LEDGER_PAD - i * LEDGER_PITCH - 3;
            text(ui.body, LABELS[i], ledgerX + 18, y, CYAN, 0.95f);
            String v = value(i);
            layout.setText(ui.num, v);
            text(ui.num, v, ledgerX + LEDGER_W - 18 - layout.width, y, Puranic.BONE, 1f);
        }

        // action rows (dimmed while the name is being typed)
        for (int i = 0; i < menu.count(); i++)
            Template.menuRow(ui, batch, menu.label(i), !editing && menu.selected(i), menu.rowY(i));

        if (editing) Template.keys(ui, batch, Template.FOOT_Y, "A-Z 0-9", "TYPE", "ENTER", "SAVE", "ESC", "CANCEL");
        else Template.keys(ui, batch, Template.FOOT_Y, "W/S", "SELECT", "ENTER", "CONFIRM", "ESC", "BACK");
        batch.end();
    }
}