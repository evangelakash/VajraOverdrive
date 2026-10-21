package vajra.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.net.InetSocketAddress;
import vajra.flow.Flow;
import vajra.flow.FlowState;
import vajra.net.LanBeacon;
import vajra.net.Msg;
import vajra.net.Net;
import vajra.net.RelayTransport;
import vajra.net.Session;
import vajra.net.Transport;
import vajra.net.UdpTransport;

/**
 * THE NETPLAY GATE — one button to host, one to join, and nothing to install.
 *
 * <p><b>HOST A RUN</b> binds the game's port, shouts the room on the LAN (so a friend on the
 * same network finds it with no typing at all) and opens the door on the router in the
 * background (so the same room answers from the internet, with no port forwarding). The
 * lobby prints the one code the friend types.
 *
 * <p><b>FIND A RUN</b> lists the rooms that can be found — the LAN's shout and, when a
 * link is set, the rooms its server holds — and takes one typed line: an invite
 * {@code ABCD@address:port} joins that room (knocking DIRECTLY first, then the relay at
 * the same address, so the friend never has to know which kind of door the host opened);
 * a bare address sets the link, and its rooms list.
 */
public class NetScreen implements FlowState {
    static final String[] ROWS = { "HOST A RUN", "FIND A RUN", "BACK" };
    private static final int HOST = 0, NEARBY = 1, BACK = 2;
    private static final float ROW_Y = 380, PITCH = 56, HALF_W = 200, HALF_H = 22;

    /** The join panel: room rows centred on the same column as the field box; the
     *  bands run the full working width so a click anywhere on a row is on the row. */
    private static final float LIST_Y = 400, LIST_PITCH = 30;
    private static final String BACK_ROW = "BACK";
    /** How long one knock waits for a WELCOME before the next address (or the relay) is tried. */
    private static final long KNOCK_MS = 3500;
    /** The one field to paste the host's line — drawn as a real box, every frame. */
    private static final float FIELD_Y = 505, FIELD_HALF_W = 310, FIELD_H = 34;
    /** Where the field's words sit. Drawn text hangs BELOW the y it is given (measured at
     *  [y-11, y-1] on this font), so centring it in the box asks for the box's mid-line —
     *  FIELD_Y, the box's floor, put the words on the lip with 23px of air above them. */
    private static final float FIELD_TEXT_Y = FIELD_Y + 13;

    private final Flow flow;
    private final Ui ui;
    private final SpriteBatch batch;
    private final UiMenu menu;
    private final LineInput line = new LineInput();
    private String status = "";

    /** True while the join panel is up. */
    private boolean joining;
    /** The cursor over the join panel's rows. */
    private int pick;

    /** A join in flight: the direct attempt, and the relay attempt that follows it. */
    private Pending pending;

    /** The rooms the linked server holds, asked quietly every 1.5 s while the panel is up. */
    private Listings listings;

    /** One attempt at a door: the session it made and the clock it has left. */
    private static final class Pending {
        final Session session;
        final String room;
        final InetSocketAddress addr;
        final long deadline;
        /** Every address already knocked on for this room, so a retry never repeats one. */
        final java.util.Set<InetSocketAddress> tried;
        Pending(Session session, String room, InetSocketAddress addr, long deadline,
                java.util.Set<InetSocketAddress> tried) {
            this.session = session;
            this.room = room;
            this.addr = addr;
            this.deadline = deadline;
            this.tried = tried;
            tried.add(addr);
        }
    }

    /** The relay attempt, run off the frame so the menu never freezes. */
    private volatile RelayTransport relayTransport;
    private volatile String relayError;
    private Thread relayThread;

    public NetScreen(Flow flow, Ui ui, SpriteBatch batch) {
        this.flow = flow;
        this.ui = ui;
        this.batch = batch;
        this.menu = new UiMenu(ROWS, ROW_Y, PITCH, HALF_W, HALF_H);
    }

    @Override
    public void update(float dt) {
        if (line.isActive()) return; // typing owns the frame
        if (pending != null) { // a knock in flight
            pending.session.update();
            if (pending.session.state() == Session.State.LIVE) {
                Net.session = pending.session;
                pending = null;
                joining = false; // the panel does not stay up under the lobby
                stopEar(); // the room is found: the LAN ear has nothing left to do
                flow.push(new LobbyScreen(flow, ui, batch));
                return;
            }
            if (System.currentTimeMillis() > pending.deadline) { // nobody answered the knock
                String room = pending.room;
                InetSocketAddress addr = pending.addr;
                java.util.Set<InetSocketAddress> pendingTried = pending.tried;
                pending.session.close();
                pending = null;
                // THE ROOM MAY BE RIGHT HERE: the link a host shares is its public address,
                // which a pilot on the host's own network may not be able to reach. The
                // room shouts its LAN address on the wire — look it up by its four
                // letters and knock there. One link, either kind of network.
                // a host on several networks shouts from several addresses: try each once
                java.util.Set<InetSocketAddress> tried = pendingTried;
                if (Net.ear != null)
                    for (LanBeacon.Found near : Net.ear.candidates(room))
                        if (!tried.contains(near.addr)) {
                            status = "FOUND THE ROOM ON THIS NETWORK";
                            knock(room, near.addr, tried);
                            return;
                        }
                startRelay(room, addr); // the second door: the relay at the same address
                return;
            }
        }
        if (relayTransport != null) { // the relay answered
            RelayTransport net = relayTransport;
            relayTransport = null;
            relayThread = null;
            try {
                Net.session = Session.join(net, vajra.profile.Profiles.active().name,
                        net.hostAddress());
                stopEar();
                flow.push(new LobbyScreen(flow, ui, batch));
            } catch (Exception e) {
                status = "THE RELAY REFUSED THE ROOM";
            }
            return;
        }
        if (relayError != null && pending == null && relayTransport == null) {
            status = relayError;
            relayError = null;
            relayThread = null;
        }
    }

    @Override
    public void dispose() {
        // the screen is leaving: nothing it started may outlive it — a socket, a relay
        // connection or the LAN ear
        if (pending != null) {
            pending.session.close();
            pending = null;
        }
        if (relayTransport != null) {
            relayTransport.close();
            relayTransport = null;
        }
        stopEar();
    }

    @Override
    public boolean keyDown(int keycode) {
        if (line.key(keycode)) return true; // typing owns every key while the line is open
        if (keycode == Input.Keys.ESCAPE) {
            if (joining) {
                joining = false;
                stopEar();
            } else {
                flow.pop();
            }
            return true;
        }
        if (joining) return joinKey(keycode);
        if (menu.keyDown(keycode)) return true;
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            activate();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char c) {
        if (line.isActive()) return line.typed(c);
        if (joining && c >= 32 && c != 127) { // typing means "I have a link from the host"
            line.open("JOIN CODE", "ABCD@ip:port · ROOM:ABCD · a bare ip:port sets the link", this::admit);
            return line.typed(c);
        }
        return false;
    }

    @Override
    public boolean touchDown(int sx, int sy, int pointer, int button) {
        if (line.isActive()) return true; // text, not rows
        if (joining) {
            int row = listHit(ui.at(sx, sy));
            if (row >= 0) {
                pick = row;
                choose();
                return true;
            }
            if (fieldHit(ui.at(sx, sy))) { // the box is clicked: its carets come on
                line.open("TYPE A LINK", "ABCD@ip:port · ROOM:ABCD · a bare ip:port sets the link", this::admit);
                status = "";
            }
            return true;
        }
        if (menu.click(ui.at(sx, sy)) >= 0) {
            activate();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int sx, int sy) {
        if (joining) {
            int row = listHit(ui.at(sx, sy));
            if (row >= 0) pick = row;
            return false;
        }
        menu.hover(ui.at(sx, sy));
        return false;
    }

    private void activate() {
        int sel = menu.selected();
        if (sel == HOST) host();
        else if (sel == NEARBY) openJoin();
        else flow.pop();
    }

    /** What the door opener found: the public address and whether the port was opened. */
    // ---- the server-facing room list (the relay's side of the join panel) --------------------

    /** The join panel's second source: a quiet thread asks the linked server for its
     *  live rooms every 1.5 s, so the friend sees the rooms that exist across the
     *  network just as the LAN ear hears the shout on the wire. Rooms are read, never
     *  booted: a listing ask spawns nothing. */
    private static final class Listings {
        private volatile InetSocketAddress at;
        private volatile RelayTransport.RoomInfo[] got;
        private volatile boolean answered;
        private final Thread thread;
        private volatile boolean run = true;

        Listings() {
            thread = new Thread(this::loop, "vajra-listings");
            thread.setDaemon(true);
            thread.start();
        }

        void target(InetSocketAddress a) {
            if (a != null) { // a new aim is an empty table at once: an old one is a lie
                at = a;
                got = new RelayTransport.RoomInfo[0];
                answered = false;
            }
        }

        InetSocketAddress target() { return at; }

        RelayTransport.RoomInfo[] rooms() { return got; }

        boolean answered() { return answered; }

        void stop() {
            run = false;
            thread.interrupt();
        }

        private void loop() {
            while (run) {
                InetSocketAddress a = at;
                if (a == null) {
                    sleep(600);
                    continue;
                }
                RelayTransport.RoomInfo[] fresh = RelayTransport.rooms(a);
                got = fresh;
                answered = fresh != null;
                sleep(1500);
            }
        }

        private static void sleep(int ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---- hosting -------------------------------------------------------------------------------

    /** ONE button, ONE link, both networks: the game's port is bound, the room is shouted
     *  on the LAN, and the router's door is opened in the background. The lobby prints one
     *  code — the public address when the door opened, this machine's LAN address when it
     *  did not — and that single link is all a friend needs, near or far. */
    private void host() {
        String name = vajra.profile.Profiles.active().name;
        String room = roomCode();
        Transport net;
        stopEar(); // a LAN ear still holding this machine's port would make the bind fail
        try {
            net = new UdpTransport(UdpTransport.PORT);
        } catch (Exception e) {
            status = "PORT " + UdpTransport.PORT + " BUSY — QUIT THE OTHER LOBBY";
            return;
        }
        Net.session = Session.host(net, name);
        Net.room = room;
        try {
            Net.beacon = new LanBeacon.Host(room, name, UdpTransport.PORT); // the LAN shout
        } catch (Exception e) {
            // no shout: the room still works, the friend just types the link
        }
        Net.openDoor(UdpTransport.PORT); // the internet door, opened in the background
        flow.push(new LobbyScreen(flow, ui, batch));
    }

    /** Four letters that can't be confused: no I, no O. */
    static String roomCode() {
        String letters = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        StringBuilder b = new StringBuilder(4);
        java.util.Random rng = new java.util.Random();
        for (int i = 0; i < 4; i++) b.append(letters.charAt(rng.nextInt(letters.length())));
        return b.toString();
    }

    // ---- joining -------------------------------------------------------------------------------

    /** The join panel: the rooms the LAN can be heard, and a typed code for the internet. */
    private void openJoin() {
        joining = true;
        pick = 0;
        status = "";
        if (Net.ear == null) {
            try {
                Net.ear = new LanBeacon.Ear();
            } catch (Exception e) {
                status = "THE LAN EAR COULD NOT OPEN — TYPE A LINK";
            }
        }
        if (listings == null) {
            listings = new Listings();
            listings.target(RelayTransport.relayAddr());
        }
    }

    private void stopEar() {
        if (Net.ear != null) {
            Net.ear.close();
            Net.ear = null;
        }
        if (listings != null) {
            listings.stop();
            listings = null;
        }
    }

    /** What the join panel lists: the LAN's shout first, the linked server's rooms
     *  beneath — at most five of each. A server section only exists when it ANSWERED:
     *  a dead or unset link lists nothing instead of a lie. */
    private RelayTransport.RoomInfo[] relayList() {
        RelayTransport.RoomInfo[] r = listings == null ? null : listings.rooms();
        if (r == null) return new RelayTransport.RoomInfo[0];
        return r.length > 5 ? java.util.Arrays.copyOfRange(r, 0, 5) : r;
    }

    private int joinRowCount() {
        return rows().size() + relayList().length + 1; // the rooms, then BACK — typing is the field, not a row
    }

    /** The rows of the join panel: the rooms heard, then TYPE A CODE, then BACK. */
    private java.util.List<LanBeacon.Found> rows() {
        java.util.List<LanBeacon.Found> found = Net.ear == null
                ? java.util.Collections.emptyList() : Net.ear.rooms();
        return found.size() > 5 ? found.subList(0, 5) : found;
    }

    private int listHit(com.badlogic.gdx.math.Vector2 m) {
        for (int i = 0; i < joinRowCount(); i++)
            if (Template.box(m, Template.COL - FIELD_HALF_W, listY(i) - 11,
                    Template.COL + FIELD_HALF_W, listY(i) + 7)) return i;
        return -1;
    }

    /** The input box is its own control, not a list row: clicking it starts typing. */
    private boolean fieldHit(com.badlogic.gdx.math.Vector2 m) {
        return Template.box(m, Template.COL - FIELD_HALF_W, FIELD_Y - 10,
                Template.COL + FIELD_HALF_W, FIELD_Y + 16);
    }

    private float listY(int i) {
        return LIST_Y - i * LIST_PITCH;
    }

    private boolean joinKey(int keycode) {
        if (keycode == Input.Keys.W || keycode == Input.Keys.UP) {
            pick = UiMenu.wrap(pick, -1, joinRowCount());
            return true;
        }
        if (keycode == Input.Keys.S || keycode == Input.Keys.DOWN) {
            pick = UiMenu.wrap(pick, 1, joinRowCount());
            return true;
        }
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            choose();
            return true;
        }
        return false;
    }

    private void choose() {
        java.util.List<LanBeacon.Found> near = rows();
        if (pick < near.size()) {
            LanBeacon.Found f = near.get(pick);
            knock(f.room, f.addr);
            return;
        }
        RelayTransport.RoomInfo[] far = relayList();
        if (pick < near.size() + far.length) {
            RelayTransport.RoomInfo f = far[pick - near.size()];
            startRelay(f.code, listings.target()); // the server's room: straight to the relay
            return;
        }
        joining = false; // the BACK row
        stopEar();
    }

    /** A typed link — the ONE line a friend can be given, doing the right thing by shape:
     *  an invite joins the room, a room code is looked up (LAN wire first, linked server
     *  second), and a bare address POINTS the game at a server — saved to relay.txt and
     *  applied live, so its rooms list without another word. */
    private void admit(String text) {
        String[] invite = inviteOf(text);
        if (invite != null) {
            String host = invite[1];
            int c = host.lastIndexOf(':');
            try {
                knock(invite[0], new InetSocketAddress(host.substring(0, c),
                        Integer.parseInt(host.substring(c + 1))));
            } catch (NumberFormatException bad) {
                status = "THE PORT DOCTRINE: A NUMBER AFTER THE COLON";
            }
            return;
        }
        String room = roomOf(text);
        if (room != null) {
            knock(room, null); // the room on the linked relay
            return;
        }
        String link = relayCheck(text);
        if (link == null) {
            status = "ONE LINK AT A TIME — ABCD@ip:port OR ip:port";
            return;
        }
        RelayTransport.RELAY = link;
        try {
            java.nio.file.Files.writeString(java.nio.file.Path.of("relay.txt"), link + "\n");
        } catch (Exception gone) { // the live link still holds either way
        }
        if (listings != null) {
            listings.target(RelayTransport.relayAddr()); // the list follows the new link
        }
        status = "THE LINK NOW " + link + " · ITS ROOMS LIST HERE";
    }

    /** Knock on a door: DIRECTLY first, and on the relay at the same address if nobody
     *  answers — the friend never has to know which kind of door the host opened. A bare
     *  room code asks the wire first (the LAN shouts live rooms); a relay-door invite
     *  (its port is not the game's) goes to the relay at once — no game answers there. */
    private void knock(String room, InetSocketAddress addr) {
        knock(room, addr, new java.util.HashSet<>());
    }

    private void knock(String room, InetSocketAddress addr, java.util.Set<InetSocketAddress> tried) {
        if (addr == null) {
            LanBeacon.Found near = Net.ear == null ? null : Net.ear.room(room);
            if (near != null) {
                status = "FOUND THE ROOM ON THIS NETWORK";
                knock(room, near.addr); // the four letters found it: no server link needed
                return;
            }
            if (RelayTransport.linkedDefault() && (listings == null || !listings.answered())) {
                // an unlinked machine has nowhere to dial: its own loopback is not a room
                status = "NO PLACE TO DIAL — TYPE THE HOST'S LINE OR ITS INVITE";
                return;
            }
            startRelay(room, null);
            return;
        }
        if (!room.isEmpty() && addr.getPort() != UdpTransport.PORT) {
            // a relay-door invite: the address is the SERVER's — no game port answers
            // there, so the direct knock is 2.5 s of guaranteed dead silence. The
            // relay is tried FIRST, as the only door this invite names.
            startRelay(room, addr);
            return;
        }
        try {
            Transport net = new UdpTransport(0); // an ephemeral client port
            Session s = Session.join(net, vajra.profile.Profiles.active().name, addr);
            pending = new Pending(s, room, addr, System.currentTimeMillis() + KNOCK_MS, tried);
            status = "KNOCKING ON " + addr + "…";
        } catch (Exception e) {
            startRelay(room, addr);
        }
    }

    /** The second door: the relay at the same address, tried off the frame. */
    private void startRelay(String room, InetSocketAddress addr) {
        if (relayThread != null) return;
        final String code = room == null || room.isEmpty() ? "ABCD" : room;
        relayThread = new Thread(() -> {
            try {
                relayTransport = RelayTransport.join(code,
                        vajra.profile.Profiles.active().name, addr);
            } catch (Exception e) {
                relayError = e.getMessage() == null ? "NO RELAY ANSWERED THERE" : e.getMessage();
            }
        }, "vajra-relay-join");
        relayThread.setDaemon(true);
        relayThread.start();
        status = addr == null ? "DIALING THE RELAY…" : "TRYING THE RELAY AT " + addr + "…";
    }

    // ---- the relay server link -----------------------------------------------------------------

    /** The relay-link law: "host" rides the default 27040, "host:port" stands as
     *  typed — shape only (a slow DNS lookup would freeze the frame; the dial
     *  reports what it found). */
    static String relayCheck(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        if (s.matches("[^\\s:]+")) return s + ":27040";
        if (s.matches("[^\\s]+:[0-9]+")) return s;
        return null;
    }

    /** "room:ABCD", "ROOM(abcd)", a bare four-letter token — or the invite the host
     *  shares: ABCD@192.168.1.9:26000, the room code and the door in one string. */
    static String roomOf(String raw) {
        return inviteOf(raw) != null ? null // the invite carries the room whole
                : roomPartOf(raw);
    }

    /** Splits "ABCD@host:port" (any room-code shape left of the @) from its address. */
    static String[] inviteOf(String raw) {
        if (raw == null) return null;
        String s = raw.strip();
        int at = s.indexOf('@');
        if (at < 1 || at == s.length() - 1) return null;
        String room = roomPartOf(s.substring(0, at)); // the room uppercases itself
        String relay = s.substring(at + 1).strip();   // the host name keeps its spelling
        if (room == null || !relay.matches(".+:[0-9]+")) return null;
        return new String[] { room, relay };
    }

    /** The room-code half alone: the old shapes, no @ accounted for here. */
    static String roomPartOf(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase().replace(' ', ':').replace('-', ':');
        int at = s.indexOf("ROOM");
        if (at >= 0) s = s.substring(at + 4);
        while (s.startsWith(":")) s = s.substring(1);
        return s.matches("[A-Z]{4}") ? s : null; // a bare 4-letter token is a room too
    }

    // ---- the paint -----------------------------------------------------------------------------

    @Override
    public void render() {
        ui.shapes.setProjectionMatrix(ui.camera.combined);
        ui.beginShapes(ShapeRenderer.ShapeType.Filled);
        Template.dim(ui, 1f); // opaque: its own screen
        Ornament.frame(ui);
        Ornament.titleMarkers(ui, "NETPLAY", Template.TITLE_Y);
        if (joining) {
            java.util.List<LanBeacon.Found> found = rows();
            for (int i = 0; i < found.size(); i++) {
                ui.shapes.setColor(vajra.screen.SeatColors.of(0)); // the seat swatch leads its row
                ui.shapes.rect(Template.COL - FIELD_HALF_W + 10, listY(i) - 9, 9, 9);
            }
            // the input box: always there, a little lighter when it is being typed in
            ui.shapes.setColor(line.isActive()
                    ? new Color(0.16f, 0.16f, 0.21f, 1f) : new Color(0.11f, 0.11f, 0.14f, 1f));
            ui.shapes.rect(Template.COL - FIELD_HALF_W, FIELD_Y - 10,
                    FIELD_HALF_W * 2, FIELD_H);
            ui.shapes.setColor(Color.GRAY);
            ui.shapes.rect(Template.COL - FIELD_HALF_W, FIELD_Y - 10,
                    FIELD_HALF_W * 2, 2); // a bright lip under the fill, so it reads as a field
        } else {
            Ornament.markers(ui, Template.COL, menu.label(menu.selected()), menu.rowY(menu.selected()));
        }
        ui.shapes.end();

        batch.setProjectionMatrix(ui.camera.combined);
        batch.begin();
        Template.title(ui, batch, "NETPLAY", Template.TITLE_Y, 1);
        if (joining) {
            drawJoinPanel();
        } else {
            ui.centered(batch, ui.menu,
                    "ONE LINK FOR BOTH — HOST OPENS THE DOOR, FIND LISTS THE ROOMS", 470);
            ui.centered(batch, ui.menu,
                    line.isActive() ? line.title() + ": " + line.text() + "_" : status, 505);
            for (int i = 0; i < menu.count(); i++)
                Template.menuRow(ui, batch, menu.label(i), menu.selected(i), menu.rowY(i));
            if (line.isActive()) // the keys row answers to the field while it owns the input
                Template.keys(ui, batch, Template.FOOT_Y, "LETTERS", "TYPE", "ENTER", "SEND", "ESC", "ABANDON");
            else
                Template.keys(ui, batch, Template.FOOT_Y, "W/S", "SELECT", "ENTER", "CONFIRM", "ESC", "BACK");
        }
        batch.end();
    }

    /** The find screen: the input box on top, the rooms found beneath — nothing else. */
    private void drawJoinPanel() {
        java.util.List<LanBeacon.Found> near = rows();
        RelayTransport.RoomInfo[] far = relayList();
        int i = 0;
        if (near.isEmpty() && far.length == 0)
            // one pitch ABOVE the list: BACK still owns row 0, so the note cannot land on it
            ui.centered(batch, ui.menu, listings != null && listings.answered()
                    ? "NO LIVE ROOMS YET — HOST A RUN, OR ASK THE HOST FOR ITS INVITE"
                    : "NO ROOMS HEARD — ASK THE HOST FOR ITS LINK OR INVITE",
                    LIST_Y + LIST_PITCH);
        for (LanBeacon.Found f : near) {
            ui.menu.setColor(pick == i ? Color.WHITE : Color.GRAY);
            ui.centered(batch, ui.menu, f.label(), listY(i));
            i++;
        }
        for (RelayTransport.RoomInfo f : far) {
            ui.menu.setColor(pick == i ? Color.WHITE : Color.GRAY);
            ui.centered(batch, ui.menu, f.code + " · " + f.pilots + " OF 4", listY(i));
            i++;
        }
        ui.menu.setColor(pick == i ? Color.WHITE : Color.GRAY);
        ui.centered(batch, ui.menu, BACK_ROW, listY(i));
        ui.menu.setColor(Color.WHITE);
        // the box: the field while it is typed, a status when there is one,
        // and a two-word placeholder when the field has never been touched
        ui.centered(batch, ui.menu, line.isActive()
                ? line.title() + ": " + line.text() + "_"
                : (status.isEmpty() ? "TYPE A LINK…" : status), FIELD_TEXT_Y);
        ui.menu.setColor(Color.GRAY);
        ui.centered(batch, ui.menu, "ABCD@ip:port JOINS · a bare ip:port SETS THE SERVER", 480);
        ui.menu.setColor(Color.WHITE);
        Template.keys(ui, batch, Template.FOOT_Y, "W/S", "PICK", "ENTER", "JOIN", "ESC", "BACK");
    }
}
