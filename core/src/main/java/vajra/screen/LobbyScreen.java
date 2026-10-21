package vajra.screen;

import vajra.audio.Audio;
import vajra.audio.Sfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import vajra.flow.Flow;
import vajra.flow.FlowState;
import vajra.net.LanBeacon;
import vajra.net.Msg;
import vajra.net.Net;
import vajra.net.RelayTransport;
import vajra.net.Session;
import vajra.net.UdpTransport;

/** The lobby: the roster of pilots, who is ready, who is talking, and — for the host —
 *  the invite code. Voice is live the whole time; hold V to talk and a talking pilot's
 *  diamond lights beside their name. READY flips your flag; the host's START locks the
 *  run seed and every pilot's lobby hands off into the same seeded shadow race (each
 *  flies it alone; the roster's book keeps everyone's best). ESC or LEAVE drops out. */
public class LobbyScreen implements FlowState {
    private static final float MSG_Y = 518;   // the one message line: code, status, or seed
    private static final float ROSTER_Y = 412, ROSTER_PITCH = 28;
    private static final float ROW_Y = 230, PITCH = 44, HALF_W = 200, HALF_H = 22;

    // the pilots table's columns, relative to the screen centre: who, ready, ping, best flight
    private static final float COL_SWATCH = -238, COL_NAME = -216, COL_READY = 10,
            COL_PING = 150, COL_PTS = 232;

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private Session session;
    private final String[] rows;   // READY / [START] / LEAVE / BACK — the host gets START
    private final UiMenu menu;
    private boolean handed;        // this lobby instance already pushed the run
    private String note = "";      // the start block reason, until it stops being true
    private long noteEpoch = -1;   // the roster the note was raised against

    public LobbyScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.session = Net.session;
        this.rows = session != null && session.isHost()
                ? new String[] {"READY", "START", "LEAVE", "BACK"}
                : new String[] {"READY", "LEAVE", "BACK"};
        this.menu = new UiMenu(rows, ROW_Y, PITCH, HALF_W, HALF_H);
        if (session != null) session.backToLobby(); // walking back in ends your own race view
    }

    @Override
    public void update(float dt) {
        if (session == null) { // a lobby without a session cannot exist — back to the title
            flow.set(new HomeScreen(flow, ui, batch));
            return;
        }
        session.voice.push(Gdx.input.isKeyPressed(Input.Keys.V)); // push-to-talk (Flow pumps the session)
        if (session.epoch() != noteEpoch) { // the roster moved: a stale note is a lie
            noteEpoch = session.epoch();
            note = "";
        }
        if (session.state() == Session.State.HANDOFF && !handed) {
            handed = true;
            // The handoff: the run becomes the whole stack on every pilot's machine alike.
            // The session rides on Net - screens come and go, it stays.
            session.enterRun();
            flow.set(new GameScreen(batch, flow, ui, session.seed()));
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            leave();
            return true;
        }
        if (keycode == Input.Keys.R && session != null && session.isHost()) {
            toRelay(); // the door would not open: the game's own relay takes the room
            return true;
        }
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
        Audio.ui(Sfx.UI_SELECT);
        if (session == null) return;
        int sel = menu.selected();
        if (sel == 0) session.toggleReady();
        else if (rows[sel].equals("START")) {
            session.start();
            note = session.state() == Session.State.HANDOFF
                    ? "" : "ALL PILOTS MUST READY"; // why the seed did not lock
        } else leave();
    }

    private void leave() {
        Net.close(); // leave() + drop the reference: the lobby is where sessions die
        flow.set(new HomeScreen(flow, ui, batch)); // the lobby is a root: leaving it is the title
    }

    @Override
    public void render() {
        float cx = Ui.UI_W / 2f;

        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: its own screen
        Ornament.frame(ui);
        Ornament.titleMarkers(ui, "LOBBY", Template.TITLE_Y);
        if (session != null) {
            Ornament.markers(ui, cx, menu.label(menu.selected()), menu.rowY(menu.selected()));
            // the table's rule and every row's seat swatch + the talking diamond
            float plateTop = ROSTER_Y + 44, plateBot = ROSTER_Y - 3 * ROSTER_PITCH - 22;
            Puranic.plate(ui, cx - 266, plateBot, 532, plateTop - plateBot, 0.92f, 0.85f);
            Puranic.c(ui, Puranic.COPPER, 0.6f);
            ui.shapes.rect(cx - 244, ROSTER_Y + 8, 488, 1.5f);
            for (int i = 0; i < session.count(); i++) {
                Msg.Member member = session.member(i);
                float y = rosterY(i);
                Color seat = SeatColors.of(member.id);
                ui.shapes.setColor(seat);
                ui.shapes.rect(cx + COL_SWATCH, y - 6, 12, 12);
                // your own row lights from the key you are holding; the others from what
                // the wire says they are saying
                boolean talking = member.id == session.myId()
                        ? session.voice.transmitting()
                        : session.voice.talkingOf(member.id);
                if (talking) diamond(cx + COL_SWATCH - 14, y);
            }
        }
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        Template.title(ui, batch, "LOBBY", Template.TITLE_Y, 1);
        ui.menu.setColor(1f, 0.93f, 0.62f, 1f);
        ui.centered(batch, ui.menu, session == null ? "NO SESSION" : message(), MSG_Y);
        ui.menu.setColor(Color.WHITE);
        String door = doorLine();
        if (!door.isEmpty()) {
            ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
            ui.centered(batch, ui.small, door, MSG_Y - 28);
            ui.small.setColor(Color.WHITE);
        }
        if (session != null) {
            float head = ROSTER_Y + 30; // the column labels over the rule
            ui.small.setColor(Puranic.COPPER[0], Puranic.COPPER[1], Puranic.COPPER[2], 1f);
            ui.small.draw(batch, "PILOT", cx + COL_NAME, head);
            ui.small.draw(batch, "STATUS", cx + COL_READY, head);
            ui.small.draw(batch, "PING", cx + COL_PING, head);
            ui.right(batch, ui.small, "BEST", cx + COL_PTS + 14, head);
            ui.small.setColor(Color.WHITE);
            for (int i = 0; i < session.count(); i++) {
                Msg.Member member = session.member(i);
                float y = rosterY(i);
                String name = member.name + (member.id == 0 ? " (HOST)" : "");
                ui.body.setColor(SeatColors.of(member.id));
                ui.body.draw(batch, name, cx + COL_NAME, y);
                ui.body.setColor(member.ready ? SeatColors.GO : SeatColors.WAIT);
                ui.body.draw(batch, member.ready ? "READY" : "NOT READY", cx + COL_READY, y);
                int ping = session.pingTo(member.id);
                ui.body.setColor(ping > 0 && ping > 90 ? SeatColors.WAIT
                        : ping > 0 ? SeatColors.GO : Color.GRAY);
                ui.body.draw(batch, ping > 0 ? Integer.toString(ping) : "-", cx + COL_PING, y);
                ui.body.setColor(Puranic.BONE[0], Puranic.BONE[1], Puranic.BONE[2], 0.8f);
                ui.right(batch, ui.body, member.points > 0
                        ? String.format(java.util.Locale.US, "%,d", member.points) : "-",
                        cx + COL_PTS + 14, y);
                ui.body.setColor(Color.WHITE);
            }
        }
        for (int i = 0; i < menu.count(); i++)
            Template.menuRow(ui, batch, menu.label(i), menu.selected(i), menu.rowY(i));
        Template.keys(ui, batch, Template.FOOT_Y,
                "W/S", "SELECT", "ENTER", "CONFIRM", "V", "TALK", "ESC", "LEAVE");
        batch.end();
    }

    private float rosterY(int i) {
        return ROSTER_Y - i * ROSTER_PITCH;
    }

    /** The one message line: the host's invite code, or whatever just happened. The code is
     *  the one thing a friend needs, so it is never hidden by a note — the note rides with it. */
    private String message() {
        if (session.state() == Session.State.JOINING) return "KNOCKING — WAITING FOR THE HOST";
        if (session.state() == Session.State.LOST)
            return session.status().isEmpty() ? "SESSION CLOSED" : session.status();
        if (session.state() == Session.State.HANDOFF) return "SEED LOCKED — ENTERING THE RUN";
        if (session.state() == Session.State.RUN) return "RACE IN PROGRESS — READY UP TO FLY AGAIN";
        if (session.isHost())
            return "CODE " + invite() + " · SHARE IT"; // the code line stays pure: a long note here would run the code off the screen
        if (!note.isEmpty()) return note; // a joiner sees why the start whistled, too
        if (!session.voice.hasMic()) return "NO MICROPHONE — LISTEN ONLY";
        return "HOLD V TO TALK";
    }

    /** The host's code: the room and the door, in the form the friend types. ONE link:
     *  the public address when the router's door opened, this machine's LAN address when
     *  it did not. A friend on this network finds the room in the list anyway, and a
     *  friend far away types the public one — the same string either way. */
    private String invite() {
        if (session.transport() instanceof RelayTransport)
            return ((RelayTransport) session.transport()).invite();
        vajra.net.Nat.Result d = Net.door;
        if (d != null && d.reachable())
            return Net.room + "@" + d.address + ":" + d.port;
        return Net.room + "@" + LanBeacon.lanAddress() + ":" + UdpTransport.PORT;
    }

    /** The second line: what the door opener found. When the door would not open, this is
     *  where the game offers its own relay — the pilot presses R and the room moves onto
     *  it. Nothing is asked of the friend and no .bat file is ever named. */
    private String doorLine() {
        if (!session.isHost()) return "";
        String n = note;
        if (session.transport() instanceof RelayTransport)
            return n.isEmpty() ? "THE RELAY CARRIES THIS ROOM" : "THE RELAY CARRIES THIS ROOM  ·  " + n;
        String doorNote = Net.doorNote;
        vajra.net.Nat.Result d = Net.door;
        if (doorNote != null && !doorNote.isEmpty())
            return d != null && d.reachable()
                    ? ("OPEN THE DOOR: " + doorNote) + (n.isEmpty() ? "" : "  ·  " + n)
                    : doorNote + "  ·  PRESS R FOR THE GAME'S OWN RELAY" + (n.isEmpty() ? "" : "  ·  " + n);
        return n; // no door note: the start-block reason rides this line alone
    }

    /** The door would not open: move this room onto the relay the game carries. */
    private void toRelay() {
        if (!session.isHost()) return;
        if (!Net.toBundledRelay()) {
            note = "THE RELAY WOULD NOT START";
            return;
        }
        session = Net.session; // the room lives on a new transport now
        note = "";
    }

    private void diamond(float x, float y) {
        float d = 5;
        ui.shapes.triangle(x, y - d, x - d * 0.8f, y, x, y + d);
        ui.shapes.triangle(x, y - d, x + d * 0.8f, y, x, y + d);
    }
}
