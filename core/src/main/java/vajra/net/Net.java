package vajra.net;

/** The one live session per process — the house's static-singleton pattern (Profiles,
 *  Settings.i before it). A session is created by the netplay screen (host or join) and
 *  outlives every screen it spawned from: the lobby pushes a run, the run pumps the
 *  session, the pause screen hands the pilot back. Closing is explicit (LEAVE/ESC); a
 *  LOST session is inert and safe to leave dangling.
 *
 *  <p>The netplay house also keeps the pieces of a NO-HASSLE connection here: the beacon a
 *  host shouts on the LAN, the ear a joiner listens with, the four-letter room code both
 *  share, and the door the opener is working on (the router's port mapping and the public
 *  address). All of them are torn down together by {@link #close()}. */
public final class Net {
    public static Session session;

    /** The four letters this room shouts and is dialled by. */
    public static String room = "";
    /** The host's beacon: it shouts the room on the LAN until the session closes. */
    public static LanBeacon.Host beacon;
    /** The joiner's ear: it listens for rooms on the LAN. */
    public static LanBeacon.Ear ear;
    /** What the door opener found: the public address and whether the port was opened. */
    public static volatile Nat.Result door;
    /** The port the door was opened for — the game port, or the relay's. */
    public static volatile int doorPort;
    /** The door opener's own progress, for the lobby's status line. */
    public static volatile String doorNote = "";

    private Net() {}

    /** Is there a session worth talking to? */
    public static boolean active() {
        return session != null && session.state() != Session.State.LOST;
    }

    /** The frame pump for any screen that hosts the session: heartbeats, packets, sweeps. */
    public static void pump() {
        if (active()) session.update();
    }

    /** Leave cleanly and drop the reference; safe to call when nothing is live. */
    public static void close() {
        if (session != null) session.leave();
        session = null;
        if (beacon != null) {
            beacon.close();
            beacon = null;
        }
        if (ear != null) {
            ear.close();
            ear = null;
        }
        room = "";
        door = null;
        doorPort = 0;
        doorNote = "";
    }

    // ---- the door: opening the router's port so the internet can knock --------------------------

    /** Start opening the door in the background: the router is told to forward this machine's
     *  UDP port to it, and the address the internet sees is learned. The lobby reads
     *  {@link #door} when it lands — the game never waits for it. */
    public static void openDoor(int localPort) {
        doorPort = localPort;
        doorNote = "OPENING THE DOOR…";
        Thread t = new Thread(() -> {
            Nat.Result r = Nat.open(localPort);
            door = r;
            doorNote = r.note;
        }, "vajra-door");
        t.setDaemon(true);
        t.start();
    }

    /** The address a friend should knock on for that port: the public one when the door
     *  opened it, this machine's LAN address otherwise. */
    public static String inviteHost(int port) {
        Nat.Result d = door;
        if (d != null && d.reachable() && (doorPort == 0 || doorPort == port)) return d.address;
        return LanBeacon.lanAddress();
    }

    /** The port a friend should knock on: the mapped one when the door opened it, the game's
     *  own port otherwise. */
    public static int invitePort(int port) {
        Nat.Result d = door;
        if (d != null && d.reachable() && (doorPort == 0 || doorPort == port)) return d.port;
        return port;
    }

    /** The whole invite: the room code and the address, in the form the joiner types. */
    public static String invite(int port) {
        return room + "@" + inviteHost(port) + ":" + invitePort(port);
    }

    /** THE ROOM MOVES ONTO THE BUNDLED RELAY: the game starts it by itself, asks the router
     *  to open its port, and the room is registered under the same four letters. The link
     *  the lobby prints becomes the relay's address — so one link still reaches the room
     *  from anywhere, and no .bat file is ever asked of the pilot. */
    public static boolean toBundledRelay() {
        if (session == null || room.isEmpty()) return false;
        try {
            RelayTransport net = RelayTransport.host(room,
                    vajra.profile.Profiles.active().name);
            session.close();
            session = Session.host(net, vajra.profile.Profiles.active().name);
            openDoor(net.relayPort());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
