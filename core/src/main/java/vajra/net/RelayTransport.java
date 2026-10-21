package vajra.net;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/** The online wire — Phase 4 (docs/MULTIPLAYER.md §4). The pilots dial OUT to a
 *  {@link vajra.relay.RelayServer} on a public box, so no home router needs a port
 *  forward: the host registers a room under a code, friends join the same code, and
 *  every session packet rides a DATA frame through it. The session above sees the same
 *  {@link Transport} surface as on a LAN — room members appear as "virtual" addresses
 *  (port = the relay's endpoint id) learned from arriving frames, so HELLO, roster
 *  addressing and the timeout sweep work untouched.
 *
 *  <p>Blocking flavour: {@link #host}/{@link #join} wait up to ~4 s for the relay's
 *  WELCOME and throw when the code is taken, the room is gone, or the relay is dead —
 *  the screen turns that into a status line. After the welcome a daemon thread mirrors
 *  {@link UdpTransport}'s shape: drain → queue, send = fire. The session's own 2 s
 *  heartbeats keep the relay mapping warm; nothing here schedules anything else. On
 *  close the side says LEAVE so the room clears at once. */
public final class RelayTransport implements Transport {
    static final byte MAGIC = 0x52;
    static final int REG = 0x01, JOIN = 0x02, DATA = 0x03, LEAVE = 0x04, LIST = 0x05;
    static final int WELCOME = 0xF1, RDATA = 0xF2, DENIED = 0xF3, ROOMS = 0xF4;

    /** The relay box this room lives on: the -Dvajra.relay launch flag, then "relay.txt"
     *  beside the install (the setup writes the friend's copy there), then this default.
     *  Dial overrides ("code@host:port" invites) beat all three. docs/MULTIPLAYER.md §4. */
    public static String RELAY = "127.0.0.1:27040";

    static {
        try {
            java.nio.file.Path f = java.nio.file.Path.of("relay.txt"); // beside the install
            if (java.nio.file.Files.exists(f)) {
                String s = java.nio.file.Files.readString(f).strip();
                if (!s.isEmpty()) RELAY = s;
            }
        } catch (Exception gone) { // no file: the default stands
        }
        String p = System.getProperty("vajra.relay");
        if (p != null && !p.isEmpty()) RELAY = p;
    }

    private final DatagramSocket socket;
    private final ConcurrentLinkedQueue<Received> in = new ConcurrentLinkedQueue<>();
    private final Map<Integer, InetSocketAddress> virtual = new HashMap<>(); // ep → fake address
    private volatile boolean open = true;
    private int myEp = -1;
    private byte[] room = new byte[4]; // the 4-letter code as the wire carries it
    private final InetSocketAddress relay; // where THIS room lives (dial override included)
    private final String relayText;        // the same, as the message line shows it

    // ---- dial ------------------------------------------------------------------------

    /** A host: claim the room code (the caller picked the four letters) and wait for
     *  the welcome. Throws with a play-readable message when it can't. */
    public static RelayTransport host(String code, String name) throws Exception {
        return host(code, name, null);
    }

    /** The host's own invite: dial an explicit relay. Whoever hosts runs the server —
     *  and when the dial finds silence, this machine's bundled one starts by itself. */
    public static RelayTransport host(String code, String name, InetSocketAddress relay)
            throws Exception {
        RelayTransport t = new RelayTransport(relay);
        try {
            t.room = code(code);
            int[] w = t.request(dial(REG, t.room, name));
            if (w == null && t.dialFail == 0 && t.localRelay() && bootLocalRelay(t.relay.getPort()))
                w = t.request(dial(REG, t.room, name)); // the relay is up now: ask again
            if (w == null) throw new IllegalStateException(t.dialWhy(true));
            t.myEp = w[0];
            t.drainLoop();
            return t;
        } catch (Exception e) {
            t.close(); // a failed dial must not leave a socket behind
            throw e;
        }
    }

    /** A joiner: the friend's code, waiting for the welcome. Same throws as host(). */
    public static RelayTransport join(String code, String name) throws Exception {
        return join(code, name, null);
    }

    /** A joiner at an explicit relay — the "ABCD@host:port" invite on the code. */
    public static RelayTransport join(String code, String name, InetSocketAddress relay)
            throws Exception {
        RelayTransport t = new RelayTransport(relay);
        try {
            t.room = code(code);
            int[] w = t.request(dial(JOIN, t.room, name));
            if (w == null) throw new IllegalStateException(t.dialWhy(false));
            t.myEp = w[0];
            t.hostEp = w[1];
            t.drainLoop();
            return t;
        } catch (Exception e) {
            t.close(); // a failed dial must not leave a socket behind
            throw e;
        }
    }

    /** The relay this room lives on — the port a friend dials. */
    public int relayPort() {
        return relay.getPort();
    }

    /** Is the relay box on this machine? Only then can the bundled one stand in for it. */
    boolean localRelay() {
        String h = relay.getHostString();
        return h.equals("127.0.0.1") || h.equals("localhost") || h.equals("0.0.0.0")
                || h.equals("::1");
    }

    /** Where the bundled java might sit, probed in order: the install's jre (Windows
     *  shape, then any shape), the Mac app's two runtimes, last the PATH's java. */
    private static final String[] RELAY_JAVAS = {
            "jre/bin/java.exe", "jre/bin/java",
            "jre-mac-arm/bin/java", "jre-mac-x64/bin/java", "java"};
    /** Where the relay's jars sit alongside the game — installed, then built. */
    private static final String[] RELAY_LIBS = {
            "relay/lib", "relay/build/install/relay/lib", "../relay/build/install/relay/lib"};

    /** No relay answered the host's dial: start the one bundled with this install so
     *  the hosting machine becomes the server itself, then give it a moment. Quietly
     *  returns false when no runtime and no jars can be found — the dial's own
     *  failure line takes over from there. */
    static boolean bootLocalRelay(int port) {
        File lib = null;
        for (String p : RELAY_LIBS) {
            File f = new File(p);
            if (f.isDirectory()) { lib = f; break; }
        }
        if (lib == null) return false; // never shipped the relay: nothing to start
        String java = "java";          // the PATH's, unless a bundled one shows up
        for (String p : RELAY_JAVAS) {
            File f = new File(p);
            if (f.isFile() && f.canExecute()) { java = f.getPath(); break; }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(java, "-cp", lib.getPath() + "/*",
                    "vajra.relay.RelayServer", Integer.toString(port));
            pb.redirectErrorStream(true);
            // the log goes somewhere writable: an installed Mac app is a read-only bundle,
            // and a redirect that cannot be opened must not stop the relay from serving
            try {
                File log = new File(System.getProperty("java.io.tmpdir", "."), "vajra-relay.log");
                pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            } catch (Exception noLog) {
                // no log: the relay still serves
            }
            pb.start(); // outlives the game: the server keeps serving until stopped
            Thread.sleep(2000); // the socket binds in milliseconds; patience costs nothing
            return true;
        } catch (Exception gone) {
            return false; // the spawn refused: the host's status line says so
        }
    }

    /** The host's sharable invite: the code and the door in one string — form
     *  ABCD@203.0.113.9:27040. The friend types exactly this into JOIN A RUN.
     *
     *  <p>A relay on ANOTHER box carries that box's address. A relay on THIS machine is
     *  reachable at this machine's public address — the one the door opener found — so that
     *  is what the code carries: the LAN address would be a dead end for a friend on the
     *  internet. */
    public String invite() {
        String host = relay.getHostString();
        if (localRelay()) {
            Nat.Result d = Net.door;
            host = (d != null && d.reachable() && Net.doorPort == relay.getPort())
                    ? d.address : LanBeacon.lanAddress();
        }
        return roomCode() + "@" + host + ":" + relay.getPort();
    }

    /** The host's virtual address, learned from the WELCOME — what the session dials. */
    public InetSocketAddress hostAddress() {
        return virtualOf(hostEp);
    }

    private int hostEp = -1; // a joiner's WELCOME tells; a host's own ep is the room's hub

    private RelayTransport() throws Exception {
        this(null);
    }

    private RelayTransport(InetSocketAddress r) throws Exception {
        relay = r != null ? r : relayAddr();
        relayText = r != null ? r.getHostString() + ":" + r.getPort() : RELAY;
        socket = new DatagramSocket(0); // ephemeral port; NAT-friendly by design
    }

    /** One blocking request/reply before the daemon loop starts: x3 retries, 1.5 s a
     *  wait, then the reason flies back as the message the status line shows. */
    private int[] request(byte[] frame) {
        dialFail = 0; // silence: the relay never said a word
        for (int retry = 0; retry < 3; retry++) {
            try {
                socket.setSoTimeout(1500);
                socket.send(new DatagramPacket(frame, frame.length, relay));
                byte[] buf = new byte[64];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                byte[] d = p.getData();
                if (p.getLength() >= 6 && d[0] == MAGIC && d[1] == (byte) WELCOME)
                    return new int[]{(d[2] & 0xFF) | (d[3] & 0xFF) << 8,
                            (d[4] & 0xFF) | (d[5] & 0xFF) << 8};
                if (p.getLength() >= 3 && d[0] == MAGIC && d[1] == (byte) DENIED) {
                    dialFail = 1 + (d[2] & 0xFF); // a real no: don't retry into the same wall
                    return null;
                }
            } catch (Exception lost) {
                // UDP loses one happily: ask again
            }
        }
        return null;
    }

    /** The dial's failure as a play-readable line. 0 silence → relay dead; else the
     *  relay's deny reason + 1. */
    private int dialFail;

    /** Why the dial failed, in the seat's own words: a HOST can start its own relay
     *  (R in the lobby, or the bat); a JOINER can only aim at the right address —
     *  telling the friend to "run the relay bat" was the old bug's worst habit. */
    private String dialWhy(boolean asHost) {
        switch (dialFail) {
            case 1: return "ROOM GONE — THE HOST LEFT";
            case 2: return "ROOM FULL — 4 PILOTS MAX";
            case 3: return "CODE ALREADY TAKEN — TRY ANOTHER";
            default:
                if (asHost) return "NO SERVER AT " + relayText
                        + " — PRESS R IN THE LOBBY, OR RUN run-relay.bat";
                return "NO SERVER THERE — USE THE HOST'S INVITE (ABCD@ip:port)";
        }
    }

    private static byte[] dial(int kind, byte[] room, String name) {
        byte[] nm = name == null ? new byte[0]
                : name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] out = new byte[2 + 4 + 1 + Math.min(nm.length, 32)];
        out[0] = MAGIC;
        out[1] = (byte) kind;
        System.arraycopy(room, 0, out, 2, 4);
        out[6] = (byte) Math.min(nm.length, 32);
        System.arraycopy(nm, 0, out, 7, out.length - 7);
        return out;
    }

    /** Relay address from "host:port", relay.txt's one line. */
    public static InetSocketAddress relayAddr() {
        int c = RELAY.lastIndexOf(':');
        if (c < 1 || c == RELAY.length() - 1) // a bare host or a bare port reads as the default
            return new InetSocketAddress("127.0.0.1", 27040);
        return new InetSocketAddress(RELAY.substring(0, c), Integer.parseInt(RELAY.substring(c + 1)));
    }

    /** Is the link still the untouched default — no -D flag, no relay.txt, no in-game
     *  SERVER LINK? The join panel uses this: an unlinked machine has nothing to list
     *  and should be told the one thing to set, not shown a dead self-lookup. */
    public static boolean linkedDefault() {
        return RELAY.equals("127.0.0.1:27040");
    }

    /** One live room at a relay: the code and the pilots in it. */
    public static final class RoomInfo {
        public final String code;
        public final int pilots;

        RoomInfo(String code, int pilots) {
            this.code = code;
            this.pilots = pilots;
        }
    }

    /** The rooms a relay holds, asked right now with its own short-lived socket — two
     *  tries, 1.2 s a wait. Null when the relay says nothing; the panel then says the
     *  link has no listing. This never spawns or boots anything: a listing is a read. */
    public static RoomInfo[] rooms(InetSocketAddress relayAt) {
        byte[] ask = { MAGIC, (byte) LIST };
        for (int attempt = 0; attempt < 2; attempt++) {
            try (DatagramSocket s = new DatagramSocket(0)) {
                s.setSoTimeout(1200);
                s.send(new DatagramPacket(ask, ask.length, relayAt));
                byte[] buf = new byte[64 + 277 * 5]; // the datagram never exceeds one page
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                s.receive(p);
                byte[] d = p.getData();
                int len = p.getLength();
                if (len < 3 || d[0] != MAGIC || d[1] != (byte) ROOMS) continue;
                int n = Math.min(d[2] & 0xFF, (len - 3) / 5);
                RoomInfo[] out = new RoomInfo[n];
                for (int i = 0; i < n; i++) {
                    int o = 3 + i * 5;
                    String code = new String(d, o, 4, java.nio.charset.StandardCharsets.US_ASCII);
                    out[i] = new RoomInfo(code, d[o + 4] & 0xFF);
                }
                return out;
            } catch (Exception quiet) {
                // a lost ask is not a denied lookup: the listener is answered or isn't
            }
        }
        return null;
    }

    /** Letters → the wire's 4 bytes, folded like the server reads them. */
    static byte[] code(String raw) {
        byte[] out = new byte[4];
        for (int i = 0; i < 4; i++) {
            int ch = i < raw.length() ? Character.toUpperCase(raw.charAt(i)) : 'A';
            if (ch < 'A' || ch > 'Z') ch = 'A'; // junk reads as A, never as a different room
            out[i] = (byte) ('A' + (ch - 'A'));
        }
        return out;
    }

    // ---- the run loop ------------------------------------------------------------------

    private void drainLoop() {
        Thread reader = new Thread(this::drain, "vajra-relay");
        reader.setDaemon(true);
        reader.start();
    }

    private void drain() {
        byte[] buf = new byte[2048]; // the swarm snapshot is the largest plane (~1.4 KB)
        while (open) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p); // blocks; close() unblocks it
                byte[] d = p.getData();
                int len = p.getLength();
                if (len < 4 || d[0] != MAGIC || d[1] != (byte) RDATA) continue;
                int fromEp = (d[2] & 0xFF) | (d[3] & 0xFF) << 8;
                byte[] data = new byte[len - 4];
                System.arraycopy(d, 4, data, 0, data.length);
                while (in.size() >= 256) in.poll(); // a stalled app must not bank frames forever
                in.add(new Received(data, virtualOf(fromEp)));
            } catch (Exception e) {
                if (!open) return; // the close race, not a bad packet
            }
        }
    }

    /** "relay-<ep>" at port ep — an unresolved, stable key the session can dial back to. */
    private InetSocketAddress virtualOf(int ep) {
        synchronized (virtual) {
            InetSocketAddress a = virtual.get(ep);
            if (a == null) {
                a = InetSocketAddress.createUnresolved("relay-" + ep, ep);
                virtual.put(ep, a);
            }
            return a;
        }
    }

    /** The invite code: the same four letters the host offered. */
    public String roomCode() {
        StringBuilder b = new StringBuilder(4);
        for (byte c : room) b.append((char) ('A' + (c & 0xFF) - 'A'));
        return b.toString();
    }

    // ---- Transport -----------------------------------------------------------------------

    @Override
    public void send(byte[] data, SocketAddress to) {
        if (!open || !(to instanceof InetSocketAddress)) return;
        int ep = ((InetSocketAddress) to).getPort();
        if (ep <= 0) return; // no such room member learned from frames: not ours to route
        byte[] out = new byte[data.length + 4];
        out[0] = MAGIC;
        out[1] = (byte) DATA;
        out[2] = (byte) ep;
        out[3] = (byte) (ep >> 8);
        System.arraycopy(data, 0, out, 4, data.length);
        try {
            socket.send(new DatagramPacket(out, out.length, relay));
        } catch (Exception gone) {
            // best effort; the session's repetitions carry loss
        }
    }

    @Override
    public Received poll() {
        return in.poll();
    }

    @Override
    public void close() {
        if (!open) return;
        open = false;
        try {
            socket.send(new DatagramPacket(new byte[]{MAGIC, (byte) LEAVE}, 2, relay));
        } catch (Exception gone) {
            // the relay's sweep clears us anyway
        }
        socket.close();
    }
}
