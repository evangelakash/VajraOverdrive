package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.flow.Flow;
import vajra.flow.FlowState;
import vajra.ship.ShipDef;
import vajra.update.Updater;
import vajra.weapon.WeaponDef;
import vajra.world.LokaTable;

/** The title screen, as a page of a manuscript read through an instrument.
 *
 *  <p>The seven menu rows are seven palm leaves strung on one cord, a pothi: the leaf in hand is
 *  pale bark with the label incised in soot ink, the rest are shaded bark with the label in bone.
 *  Down the left runs a gauge of the seven lokas (the Bhagavata's seven nether worlds, Atala to
 *  Patala), with the one this build opens marked; on the right a small readout of what the build
 *  holds. The name is set in type. The ground is soil, with the faint dial of a wheel behind it.
 *  Nothing on the screen is a bitmap.
 *
 *  <p>Rows are PLAY / MULTIPLAYER / CONTROLS / PROFILE / SETTINGS / UPDATE / QUIT. ENTER or a
 *  click activates the selected row. PLAY starts the run at once. UPDATE is the in-game swapper: it
 *  pulls the newest setup exe per vajra.update.Updater and lets the installer take over. */
public class HomeScreen implements FlowState {
    static final String[] MENU =
            {"PLAY", "MULTIPLAYER", "CONTROLS", "PROFILE", "SETTINGS", "UPDATE", "QUIT"};
    private static final float MENU_Y = 508;  // first row baseline: the seven leaves of the bundle
    private static final float PITCH = 44;    // row pitch (rows abut at HALF_H 22, no dead zone)
    private static final float HALF_W = 210;  // row hit half-width
    private static final float HALF_H = 22;   // row hit half-height
    private static final float LEAF_W = 420, LEAF_H = 36; // one leaf of the bundle

    // the title block, top to bottom: the name, a wheel divider, the line under it
    private static final float NAME_Y = 646, DIVIDER_Y = 574, SUB_Y = 552;

    // the gauge of lokas, left; the readout, right
    private static final float GAUGE_X = 96, GAUGE_TOP = 520, GAUGE_PITCH = 44;
    private static final float READ_X = 1006, READ_W = 190, READ_TOP = 520;

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final UiMenu menu;
    private final Updater updater;

    public HomeScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.menu = new UiMenu(MENU, MENU_Y, PITCH, HALF_W, HALF_H);
        updater = new Updater();
        Template.setAccent(UiTheme.GOLD_R, UiTheme.GOLD_G, UiTheme.GOLD_B); // back to brand gold
    }

    // --- the vertical budget, exposed so a test can check it collides with nothing -----------

    static float menuY() { return MENU_Y; }
    static float pitch() { return PITCH; }
    static float halfH() { return HALF_H; }
    static float dividerY() { return DIVIDER_Y; }
    static float nameY() { return NAME_Y; }

    /** Menu cursor, wrapping. Package-private: the headless test pins the wrap. */
    static int move(int sel, int dir) {
        return UiMenu.wrap(sel, dir, MENU.length);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (menu.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activate();
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
        if (sel == 0) flow.set(new GameScreen(batch, flow, ui));
        else if (sel == 1) flow.push(new NetScreen(flow, ui, batch));
        else if (sel == 2) flow.push(new ControlScreen(flow, ui, batch));
        else if (sel == 3) flow.push(new ProfileScreen(flow, ui, batch));
        else if (sel == 4) flow.push(new SettingsScreen(flow, ui, batch));
        else if (sel == 5) updater.begin();
        else Gdx.app.exit();
    }

    @Override
    public void update(float dt) {
        // nothing moves on this screen — the leaves, the gauge and the dial are all still
    }

    /** The centre of leaf i, in UI space. The label is drawn with its top at rowY. */
    private float leafCy(int i) {
        return menu.rowY(i) - 8;
    }

    @Override
    public void render() {
        float cx = Ui.UI_W / 2f;

        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: soil, bark grain, the dial, the strata
        Ornament.frame(ui);
        Puranic.divider(ui, cx, DIVIDER_Y, 200, 0.9f);

        // the pothi: a cord through the holes, then the leaves strung on it
        float lx = cx - LEAF_W / 2f;
        Puranic.cord(ui, lx + 12, leafCy(MENU.length - 1) - 24, leafCy(0) + 24);
        for (int i = 0; i < menu.count(); i++) {
            boolean on = menu.selected(i);
            Puranic.leaf(ui, lx, leafCy(i) - LEAF_H / 2f, LEAF_W, LEAF_H,
                    on ? Puranic.LEAF : Puranic.BARK, on ? 1f : 0.96f, on);
            if (on) { // a chevron on the leaf in hand
                Puranic.c(ui, Puranic.INK, 0.9f);
                ui.shapes.triangle(lx + LEAF_W - 34, leafCy(i) + 7, lx + LEAF_W - 34, leafCy(i) - 7,
                        lx + LEAF_W - 24, leafCy(i));
            }
        }
        gauge();
        readout();
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        drawName(cx);
        for (int i = 0; i < menu.count(); i++) {
            boolean on = menu.selected(i);
            ui.small.setColor(on ? Puranic.INK[0] : Puranic.COPPER[0], on ? Puranic.INK[1] : Puranic.COPPER[1],
                    on ? Puranic.INK[2] : Puranic.COPPER[2], on ? 0.7f : 0.95f);
            ui.small.draw(batch, String.format("%02d", i + 1), lx + 30, leafCy(i) + 6);
            Template.menuRow(ui, batch, menu.label(i), on, menu.rowY(i));
        }
        ui.small.setColor(1, 1, 1, 1);
        gaugeText();
        readoutText();
        if (updater.message().isEmpty()) // the hint row answers to the updater while it works
            Template.keys(ui, batch, UiTheme.FOOT_Y, "W/S", "SELECT", "ENTER / CLICK", "CONFIRM");
        else
            Template.sub(ui, batch, updater.message(), UiTheme.FOOT_Y, 0.85f);
        batch.end();
    }

    /** The name in Yatra One, larger than any other heading, with a soft shadow; the line beneath
     *  it in spaced capitals; and the double danda that ends a verse. */
    private void drawName(float cx) {
        com.badlogic.gdx.graphics.g2d.BitmapFont f = ui.title;
        float sx = f.getData().scaleX, sy = f.getData().scaleY;
        f.getData().setScale(sx * 1.55f, sy * 1.55f);
        f.setColor(0f, 0f, 0f, 0.6f);
        ui.centeredSpaced(batch, f, "VAJRA OVERDRIVE", NAME_Y - 3, 12f);
        f.setColor(Puranic.BRONZE[0], Puranic.BRONZE[1], Puranic.BRONZE[2], 1f);
        ui.centeredSpaced(batch, f, "VAJRA OVERDRIVE", NAME_Y, 12f);
        f.setColor(1, 1, 1, 1);
        f.getData().setScale(sx, sy);
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        ui.centeredSpaced(batch, ui.small, "॥   AFTER THE BHAGAVATA   •   THE SEVEN NETHER WORLDS   ॥", SUB_Y, 2f);
        ui.small.setColor(1, 1, 1, 1);
    }

    // --- the gauge of lokas (left) --------------------------------------------------------------

    private float gaugeY(int i) {
        return GAUGE_TOP - i * GAUGE_PITCH;
    }

    /** A rule down the page with one tick per loka; a solid square for the one this build opens. */
    private void gauge() {
        float bottom = gaugeY(LokaTable.COUNT - 1) - 4, top = GAUGE_TOP + 24;
        Puranic.c(ui, Puranic.COPPER, 0.6f);
        ui.shapes.rect(GAUGE_X, bottom, 1.4f, top - bottom);
        for (int i = 0; i < LokaTable.COUNT; i++) {
            boolean open = i == 0; // only Atala is built in this version
            float y = gaugeY(i) - 5;
            if (open) {
                Puranic.c(ui, Puranic.OCHRE, 1f);
                ui.shapes.rect(GAUGE_X - 5, y - 5, 11, 11);
            } else {
                Puranic.c(ui, Puranic.COPPER, 0.7f);
                ui.shapes.rect(GAUGE_X - 5, y - 5, 11, 1.4f);
                ui.shapes.rect(GAUGE_X - 5, y + 5, 11, 1.4f);
                ui.shapes.rect(GAUGE_X - 5, y - 5, 1.4f, 11);
                ui.shapes.rect(GAUGE_X + 5, y - 5, 1.4f, 11);
            }
        }
    }

    private void gaugeText() {
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        ui.small.draw(batch, "THE SEVEN LOKAS", GAUGE_X - 5, GAUGE_TOP + 52);
        for (int i = 0; i < LokaTable.COUNT; i++) {
            boolean open = i == 0;
            float y = gaugeY(i);
            ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], open ? 1f : 0.7f);
            ui.small.draw(batch, String.format("%02d", i + 1), GAUGE_X + 18, y);
            ui.menu.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], open ? 1f : 0.45f);
            ui.menu.draw(batch, LokaTable.at(i).name.toUpperCase(), GAUGE_X + 44, y + 3);
            if (open) {
                ui.small.setColor(Puranic.OCHRE[0], Puranic.OCHRE[1], Puranic.OCHRE[2], 1f);
                ui.small.draw(batch, "OPEN", GAUGE_X + 44, y - 20);
            }
        }
        ui.menu.setColor(1, 1, 1, 1);
        ui.small.setColor(1, 1, 1, 1);
    }

    // --- the readout (right) --------------------------------------------------------------------

    private void readout() {
        float h = 3 * 34 + 48, y = READ_TOP - h + 24;
        Puranic.c(ui, Puranic.PLATE, 0.85f);
        ui.shapes.rect(READ_X, y, READ_W, h);
        Puranic.border(ui, READ_X, y, READ_W, h, 0.9f);
        Puranic.c(ui, Puranic.COPPER, 0.5f);
        for (int i = 1; i < 3; i++) ui.shapes.rect(READ_X + 12, y + h - 40 - i * 34 + 8, READ_W - 24, 1);
    }

    private void readoutText() {
        float y0 = READ_TOP - 3 * 34 - 48 + 24 + 3 * 34 + 48; // the readout's top edge
        ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
        ui.small.draw(batch, "THIS BUILD", READ_X + 14, y0 - 12);
        String[][] rows = {
                {"HULLS", String.format("%02d", ShipDef.ALL.length)},
                {"ARMS", String.format("%02d", WeaponDef.ALL.length)},
                {"LOKAS OPEN", "01 / " + String.format("%02d", LokaTable.COUNT)} };
        for (int i = 0; i < rows.length; i++) {
            float y = y0 - 40 - i * 34;
            ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
            ui.small.draw(batch, rows[i][0], READ_X + 14, y);
            ui.menu.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 1f);
            ui.right(batch, ui.menu, rows[i][1], READ_X + READ_W - 14, y + 3);
        }
        ui.menu.setColor(1, 1, 1, 1);
        ui.small.setColor(1, 1, 1, 1);
    }

    @Override
    public void dispose() {
        // nothing owned: the title is set in type and drawn from shapes
    }
}
