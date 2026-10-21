package vajra.relay;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** The internet relay — Phase 4 (docs/MULTIPLAYER.md §4). Both pilots dial OUT to this
 *  process, so neither home router needs a port forward and even symmetric NAT joins fine.
 *  The host {@code REGISTER}s a room under a code it picked, everyone else {@code JOIN}s
 *  it, and from then on every session packet rides inside a DATA frame: the relay copies
 *  each frame to one room member (or all of the others for target 0) and stamps the
 *  sender's endpoint id, which the clients turn back into "virtual" addresses for the
 *  session layer. Nothing here understands the protocol it carries.
 *
 *  <p>Wire law (one UDP port, default 27040; every frame starts 0x52 'R'):
 *  <pre>
 *  client → relay: REG   0x01 [room 4][nameLen u8][name..]      (host dial; becomes the hub)
 *                  JOIN  0x02 [room 4][nameLen u8][name..]      (the friend dials the code)
 *                  DATA  0x03 [target u16][payload..]           (target 0 = the room's others)
 *                  LEAVE 0x04
 *                  LIST  0x05                                  (the lobby's room table ask)
 *  relay → client: WELCOME 0xF1 [yourEp u16][hostEp u16]        (a host's hostEp = yourEp)
 *                  DATA    0xF2 [fromEp u16][payload..]
 *                  DENIED  0xF3 [why u8]                        (0 room gone, 1 full, 2 code taken/malformed)
 *                  ROOMS   0xF4 [count u8] · count×[room 4][live u8]
 *  </pre>
 *
 *  <p>Run it anywhere Java 17 lives — on a $5 box: {@code java -jar vajra-relay-all.jar 27040}.
 *  Every game install carries a copy: the {@code run-relay.bat} beside it starts one. Capacity
 *  is 4 endpoints a room (one host, three joiners — the roster cap); an endpoint silent
 *  30 s (heartbeats fly every 2 s) stops counting. */
public final class RelayServer {
    /** 0x52 'R' — every frame's first byte. The session's own tags never leave the DATA
     *  payload, so this prefix can't collide with them. */
    static final byte MAGIC = 0x52;

    public static final int DEFAULT_PORT = 27040;
    static final int ROOM_CAP = 4;
    private static final long EP_TTL = 30_000;

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        System.out.println("vajra relay on udp/" + port + " — share ROOM:CODE with your friend");
        new RelayServer(port).run();
    }

    private static final class Endpoint {
        SocketAddress addr;
        long lastSeen;
    }

    private static final class Room {
        final String code;
        final Map<Integer, Endpoint> eps = new HashMap<>();
        int hostEp;
        int seq; // the next endpoint id within this room

        Room(String code) {
            this.code = code;
        }
    }

    private final Map<String, Room> rooms = new HashMap<>();
    private final Random rng = new Random();
    private final DatagramSocket socket;

    public RelayServer(int port) throws Exception {
        socket = new DatagramSocket(new InetSocketAddress(port));
        socket.setSoTimeout(500); // the sweep paces on receive timeouts
    }

    /** The relay's whole life: receive a frame, act, repeat. Public so a test can run one. */
    public void run() {
        byte[] buf = new byte[2048]; // the game's own socket uses the same size
        while (true) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                act(p);
            } catch (SocketTimeoutException quiet) {
                sweep(System.currentTimeMillis());
            } catch (Exception messy) {
                sweep(System.currentTimeMillis()); // a bad frame never stops the relay
            }
        }
    }

    private void act(DatagramPacket p) {
        byte[] d = p.getData();
        int len = p.getLength();
        SocketAddress from = p.getSocketAddress();
        if (len < 2 || d[0] != MAGIC) return;
        long now = System.currentTimeMillis();

        switch (d[1]) {
            case 0x01: { // REG — a host claims a live room under its own code
                String code = code(d, len);
                if (code == null) {
                    deny(from, 2);
                    return;
                }
                Room room = rooms.get(code);
                if (room != null && live(room, now)) {
                    deny(from, 2); // a live room already earned this code
                    return;
                }
                Room fresh = room != null ? room : new Room(code);
                fresh.hostEp = 0;
                fresh.seq = 0;
                fresh.eps.clear();
                rooms.put(code, fresh);
                int ep = add(fresh, from, now);
                fresh.hostEp = ep;
                send(welcome(ep, ep), from);
                System.out.println(fresh.code + " ep" + ep + " HOSTS");
                break;
            }
            case 0x02: { // JOIN a friend's code
                String code = code(d, len);
                Room room = code == null ? null : rooms.get(code);
                if (room == null || !live(room, now)) {
                    rooms.remove(code);
                    deny(from, 0);
                    return;
                }
                if (room.eps.size() >= ROOM_CAP) {
                    deny(from, 1);
                    return;
                }
                int ep = add(room, from, now);
                send(welcome(ep, room.hostEp), from);
                System.out.println(room.code + " ep" + ep + " JOINS");
                break;
            }
            case 0x03: { // DATA — route one frame across the room
                Room room = roomOf(from, now);
                if (room == null || len < 5) return;
                int fromEp = epOf(room, from);
                int target = (d[2] & 0xFF) | (d[3] & 0xFF) << 8;
                byte[] payload = new byte[len - 4];
                System.arraycopy(d, 4, payload, 0, payload.length);
                if (target == 0) { // broadcast: all other live members
                    for (Map.Entry<Integer, Endpoint> e : room.eps.entrySet())
                        if (e.getKey() != fromEp) forward(payload, fromEp, e.getValue().addr);
                } else {
                    Endpoint to = room.eps.get(target);
                    if (to != null && !to.addr.equals(from)) forward(payload, fromEp, to.addr);
                }
                break;
            }
            case 0x04: { // LEAVE
                Room room = roomOf(from, now);
                if (room != null) drop(room, from);
                break;
            }
            case 0x05: { // LIST — the join panel's room table, live rooms only
                int n = 0;
                for (Room r : rooms.values()) if (live(r, now)) n++;
                byte[] out = new byte[3 + n * 5];
                out[0] = MAGIC;
                out[1] = (byte) 0xF4;
                out[2] = (byte) n;
                int o = 3;
                for (Room r : rooms.values()) {
                    if (!live(r, now)) continue;
                    for (int i = 0; i < 4; i++) out[o++] = (byte) r.code.charAt(i);
                    out[o++] = (byte) liveCount(r, now);
                }
                send(out, from);
                break;
            }
            default: // an unknown frame is noise, not an attack
        }
    }

    // ---- frames ----------------------------------------------------------------------------

    /** The 4-letter code, folded into 'A'..'Z' so lowercase input still lands. */
    private String code(byte[] d, int len) {
        if (len < 6) return null;
        StringBuilder b = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            int ch = d[2 + i] & 0xFF;
            b.append((char) ('A' + Math.max(0, Math.min(25, ch - (ch >= 'a' ? 'a' : 'A')))));
        }
        return b.toString();
    }

    private static byte[] welcome(int yourEp, int hostEp) {
        byte[] out = new byte[6];
        out[0] = MAGIC;
        out[1] = (byte) 0xF1;
        out[2] = (byte) yourEp;
        out[3] = (byte) (yourEp >> 8);
        out[4] = (byte) hostEp;
        out[5] = (byte) (hostEp >> 8);
        return out;
    }

    private void deny(SocketAddress to, int why) {
        byte[] out = {MAGIC, (byte) 0xF3, (byte) why};
        send(out, to);
    }

    private void forward(byte[] payload, int fromEp, SocketAddress to) {
        byte[] out = new byte[payload.length + 4];
        out[0] = MAGIC;
        out[1] = (byte) 0xF2;
        out[2] = (byte) fromEp;
        out[3] = (byte) (fromEp >> 8);
        System.arraycopy(payload, 0, out, 4, payload.length);
        send(out, to);
    }

    private void send(byte[] data, SocketAddress to) {
        try {
            socket.send(new DatagramPacket(data, data.length, to));
        } catch (Exception gone) {
            // best effort, like the wire itself
        }
    }

    // ---- room book-keeping -----------------------------------------------------------------

    private int add(Room room, SocketAddress from, long now) {
        int ep = ++room.seq;
        Endpoint e = new Endpoint();
        e.addr = from;
        e.lastSeen = now;
        room.eps.put(ep, e);
        return ep;
    }

    private Room roomOf(SocketAddress from, long now) {
        for (Room room : rooms.values()) {
            for (Map.Entry<Integer, Endpoint> e : room.eps.entrySet()) {
                if (e.getValue().addr.equals(from)) {
                    e.getValue().lastSeen = now;
                    return room;
                }
            }
        }
        return null;
    }

    private int epOf(Room room, SocketAddress from) {
        for (Map.Entry<Integer, Endpoint> e : room.eps.entrySet())
            if (e.getValue().addr.equals(from)) return e.getKey();
        return 0;
    }

    private void drop(Room room, SocketAddress from) {
        Integer hit = null;
        for (Map.Entry<Integer, Endpoint> e : room.eps.entrySet())
            if (e.getValue().addr.equals(from)) hit = e.getKey();
        if (hit != null) room.eps.remove(hit);
    }

    private boolean live(Room room, long now) {
        for (Endpoint e : room.eps.values())
            if (now - e.lastSeen < EP_TTL) return true;
        return false;
    }

    /** How many endpoints of a room are still breathing — the room table's divider. */
    private static int liveCount(Room room, long now) {
        int n = 0;
        for (Endpoint e : room.eps.values())
            if (now - e.lastSeen < EP_TTL) n++;
        return n;
    }

    /** Rooms whose every member went quiet (30 s) stop existing; the code frees up. */
    private void sweep(long now) {
        for (Room room : new ArrayList<>(rooms.values()))
            if (!live(room, now)) rooms.remove(room.code);
    }
}
