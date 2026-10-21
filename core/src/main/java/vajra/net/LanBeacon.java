package vajra.net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * THE LAN BEACON — how a friend finds you without typing an address.
 *
 * <p>The host shouts on the wire: a small datagram every second carrying the room code,
 * the pilot's name and the game port. The joiner keeps an ear open and the lobby LISTS the
 * rooms it can hear. No address is read off a screen and none is typed.
 *
 * <p>Two things make the shout actually arrive, and both were learned the hard way:
 *
 * <ul>
 *   <li><b>The shout rides the game's own port</b> (plus the side port beside it). A
 *       firewall rule that lets the game work lets the shout work: a rule that opens only
 *       the game's port would otherwise leave the room list permanently empty.</li>
 *   <li><b>One socket per network interface, bound to that interface's address.</b> A
 *       broadcast sent from a wildcard socket leaves by whichever wire the routing table
 *       picks — a VPN adapter, a Hyper-V virtual switch, a Docker bridge — and dies there.
 *       Bound to the address, it leaves by the right one. This is the single most common
 *       reason a LAN discovery "does not work" on a modern Windows machine.</li>
 * </ul>
 *
 * <p>Format (little endian): MAGIC "VJRB" · version u8 · room 4 bytes · port u16 ·
 * nameLen u8 · name bytes. One datagram is one beacon.
 */
public final class LanBeacon {

    /** The beacon rides the game's own port, so the game's firewall rule covers it. */
    public static final int PORT = 26000;
    /** The side port the first beacon used: still shouted, still heard, for anything else. */
    public static final int SIDE_PORT = 26010;
    private static final byte[] MAGIC = { 'V', 'J', 'R', 'B' };
    private static final int VERSION = 1;
    /** A beacon older than this is a room that has gone quiet. */
    public static final long STALE_MS = 6000;

    private LanBeacon() {}

    /** One room heard on the wire. */
    public static final class Found {
        public final InetSocketAddress addr; // where to knock
        public final String room;
        public final String name;
        public final long heardAt;

        Found(InetSocketAddress addr, String room, String name, long heardAt) {
            this.addr = addr;
            this.room = room;
            this.name = name;
            this.heardAt = heardAt;
        }

        /** The label the lobby shows: the pilot's name, and the room they shout. */
        public String label() {
            return name + "  ·  ROOM " + room;
        }
    }

    // ---- the host's side: the shout ----------------------------------------------------------

    /** The host's beacon: a thread that shouts once a second until it is closed. */
    public static final class Host implements AutoCloseable {
        private final List<DatagramSocket> socks = new ArrayList<>();
        private final List<InetAddress> targets = new ArrayList<>();
        private final Thread thread;
        private volatile boolean open = true;
        private final byte[] packet;

        public Host(String room, String name, int gamePort) {
            packet = beacon(room, name, gamePort);
            // one socket per interface, bound to that address: the shout leaves by the
            // right wire and not by the routing table's favourite
            for (Lan lan : lanNets()) {
                // the interface's OWN broadcast address (its real prefix), then the limited one
                for (InetAddress target : new InetAddress[] { lan.broadcast, limitedBroadcast() }) {
                    if (target == null) continue;
                    DatagramSocket s = boundSocket(lan.ip);
                    if (s == null) continue;
                    socks.add(s);
                    targets.add(target);
                }
            }
            if (socks.isEmpty()) { // no interface to speak from: the limited broadcast anyway
                DatagramSocket s = null;
                try {
                    s = new DatagramSocket();
                    s.setBroadcast(true);
                } catch (SocketException ignored) {
                    // no socket: the shout does not happen
                }
                if (s != null) {
                    socks.add(s);
                    targets.add(limitedBroadcast());
                }
            }
            thread = new Thread(this::shout, "vajra-beacon");
            thread.setDaemon(true);
            thread.start();
        }

        /** A socket bound to one interface's address — the binding is the whole point. */
        private static DatagramSocket boundSocket(InetAddress ip) {
            return LanBeacon.boundSocket(ip);
        }

        private void shout() {
            int[] ports = { PORT, SIDE_PORT };
            while (open) {
                for (int i = 0; i < socks.size(); i++) {
                    for (int port : ports) {
                        try {
                            socks.get(i).send(new DatagramPacket(packet, packet.length,
                                    new InetSocketAddress(targets.get(i), port)));
                        } catch (IOException gone) {
                            // a broadcast the segment refuses is not an error
                        }
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException stopped) {
                    return;
                }
            }
        }

        @Override
        public void close() {
            open = false;
            for (DatagramSocket s : socks) s.close();
            thread.interrupt();
        }
    }

    /** A broadcast socket bound to one interface's address. {@code new DatagramSocket()} is
     *  ALREADY bound to the wildcard, so a later bind() always threw and every per-interface
     *  socket silently became the single wildcard fallback; {@code (null)} leaves it unbound. */
    static DatagramSocket boundSocket(InetAddress ip) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket(null);
            s.setBroadcast(true);
            s.setSoTimeout(500);
            s.bind(new InetSocketAddress(ip, 0));
            return s;
        } catch (Exception e) {
            if (s != null) s.close();
            return null;
        }
    }

    // ---- the guest's side: the ear ------------------------------------------------------------

    /** The guest's ear: threads that listen and keep the rooms they hear. */
    public static final class Ear implements AutoCloseable {
        private final List<DatagramSocket> sockets = new ArrayList<>();
        private final List<Thread> threads = new ArrayList<>();
        private volatile boolean open = true;
        private final List<Found> heard = Collections.synchronizedList(new ArrayList<>());

        public Ear() {
            // both ports: the game's own, and the side one. Whichever the shout reached.
            for (int port : new int[] { PORT, SIDE_PORT }) {
                try {
                    DatagramSocket s = new DatagramSocket(port);
                    s.setBroadcast(true);
                    s.setSoTimeout(500);
                    sockets.add(s);
                } catch (SocketException e) {
                    // that port is taken (this machine may be hosting): the other may answer
                }
            }
            if (sockets.isEmpty())
                throw new IllegalStateException("THE LAN EAR COULD NOT OPEN A PORT");
            for (DatagramSocket s : sockets) {
                Thread t = new Thread(() -> listen(s), "vajra-ear");
                t.setDaemon(true);
                t.start();
                threads.add(t);
            }
        }

        private void listen(DatagramSocket socket) {
            byte[] buf = new byte[256];
            while (open) {
                try {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    socket.receive(p);
                    Found f = read(p);
                    if (f != null) remember(f);
                } catch (IOException quiet) {
                    if (!open) return;
                }
            }
        }

        /** The rooms heard, one per room code, newest first, stale ones dropped. A host with
         *  Wi-Fi, a VPN and a Docker bridge shouts from several addresses; the room is listed
         *  once, at the address on a network THIS machine is also on when there is one. */
        public List<Found> rooms() {
            List<Found> live = live();
            List<Found> out = new ArrayList<>();
            for (Found f : live) {
                boolean seen = false;
                for (Found o : out) if (o.room.equals(f.room)) { seen = true; break; }
                if (seen) continue;
                List<Found> mine = candidates(f.room);
                out.add(mine.isEmpty() ? f : mine.get(0));
            }
            return out;
        }

        private List<Found> live() {
            long now = System.currentTimeMillis();
            List<Found> live = new ArrayList<>();
            synchronized (heard) {
                for (Found f : heard) if (now - f.heardAt < STALE_MS) live.add(f);
            }
            live.sort((a, b) -> Long.compare(b.heardAt, a.heardAt));
            return live;
        }

        /** Every address this room has shouted from, best first: addresses on a segment this
         *  machine is on, then the rest, each group newest first. A failed knock tries the next. */
        public List<Found> candidates(String code) {
            List<Found> near = new ArrayList<>(), far = new ArrayList<>();
            if (code != null)
                for (Found f : live()) {
                    if (!f.room.equalsIgnoreCase(code)) continue;
                    (onMySegment(f.addr.getAddress()) ? near : far).add(f);
                }
            near.addAll(far);
            return near;
        }

        /** The room with that code, or null. This is how a typed code finds a host on the
         *  same network when the address it carries is the public one. */
        public Found room(String code) {
            List<Found> c = candidates(code);
            return c.isEmpty() ? null : c.get(0);
        }

        /** Test seam: as if a beacon had just been heard. */
        void hear(Found f) {
            remember(f);
        }

        private void remember(Found f) {
            synchronized (heard) {
                for (int i = 0; i < heard.size(); i++)
                    if (heard.get(i).addr.equals(f.addr)) { // the same room shouting again
                        heard.remove(i);
                        break;
                    }
                heard.add(f);
                while (heard.size() > 16) heard.remove(0);
            }
        }

        @Override
        public void close() {
            open = false;
            for (DatagramSocket s : sockets) s.close();
            for (Thread t : threads) t.interrupt();
        }
    }

    // ---- the packet ---------------------------------------------------------------------------

    /** The beacon's bytes: MAGIC, version, room, port, name. */
    static byte[] beacon(String room, String name, int gamePort) {
        byte[] p = new byte[MAGIC.length + 1 + 4 + 2 + 1 + name.length()];
        System.arraycopy(MAGIC, 0, p, 0, MAGIC.length);
        int o = MAGIC.length;
        p[o++] = (byte) VERSION;
        for (int i = 0; i < 4; i++) p[o++] = (byte) room.charAt(i);
        p[o++] = (byte) gamePort;
        p[o++] = (byte) (gamePort >> 8);
        p[o++] = (byte) name.length();
        for (int i = 0; i < name.length(); i++) p[o++] = (byte) name.charAt(i);
        return p;
    }

    /** Reads one beacon. Null when the datagram is not one. */
    static Found read(DatagramPacket p) {
        byte[] d = p.getData();
        int len = p.getLength();
        if (len < MAGIC.length + 8) return null;
        for (int i = 0; i < MAGIC.length; i++) if (d[i] != MAGIC[i]) return null;
        int o = MAGIC.length;
        if ((d[o++] & 0xFF) != VERSION) return null;
        String room = new String(d, o, 4, java.nio.charset.StandardCharsets.US_ASCII);
        o += 4;
        int port = (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8);
        o += 2;
        int nameLen = d[o++] & 0xFF;
        if (o + nameLen > len) return null;
        String name = new String(d, o, nameLen, java.nio.charset.StandardCharsets.UTF_8);
        InetSocketAddress addr = new InetSocketAddress(p.getAddress(), port);
        return new Found(addr, room, name, System.currentTimeMillis());
    }

    // ---- the addresses ------------------------------------------------------------------------

    /** One IPv4 interface address with its REAL broadcast address and prefix length. */
    static final class Lan {
        final InetAddress ip;
        final InetAddress broadcast;
        final int prefix;

        Lan(InetAddress ip, InetAddress broadcast, int prefix) {
            this.ip = ip;
            this.broadcast = broadcast;
            this.prefix = prefix;
        }
    }

    /** Every up, non-loopback IPv4 interface address, site-local first. The broadcast and
     *  prefix come from the interface itself ({@link InterfaceAddress}, there since Java 6);
     *  the guess from the address range is only the fallback for a stack that reports none. */
    static List<Lan> lanNets() {
        List<Lan> out = new ArrayList<>();
        List<Lan> others = new ArrayList<>();
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress a = ia.getAddress();
                    if (!(a instanceof Inet4Address)) continue;
                    int prefix = ia.getNetworkPrefixLength();
                    if (prefix <= 0 || prefix > 32) prefix = guessPrefix(a);
                    InetAddress bc = ia.getBroadcast() != null ? ia.getBroadcast()
                            : subnetBroadcast(a, prefix);
                    Lan lan = new Lan(a, bc, prefix);
                    if (a.isSiteLocalAddress()) out.add(lan);
                    else others.add(lan);
                }
            }
        } catch (Throwable ignored) {
            // no interfaces to read
        }
        out.addAll(others); // a public-facing machine still shouts on its own wire
        return out;
    }

    /** This machine's own IPv4 addresses, site-local first: the wires worth shouting on. */
    static List<InetAddress> lanAddresses() {
        List<InetAddress> out = new ArrayList<>();
        for (Lan lan : lanNets()) out.add(lan.ip);
        return out;
    }

    /** Is that address on a segment one of this machine's interfaces is on? */
    static boolean onMySegment(InetAddress other) {
        if (other == null) return false;
        byte[] o = other.getAddress();
        for (Lan lan : lanNets()) {
            byte[] m = lan.ip.getAddress();
            if (m.length != o.length) continue;
            if (sameNet(m, o, lan.prefix)) return true;
        }
        return false;
    }

    /** Do two addresses share their first {@code prefix} bits? */
    static boolean sameNet(byte[] a, byte[] b, int prefix) {
        if (a.length != b.length || prefix < 0 || prefix > a.length * 8) return false;
        for (int i = 0; i < a.length && prefix > 0; i++, prefix -= 8) {
            int bits = Math.min(8, prefix);
            int mask = (0xFF << (8 - bits)) & 0xFF;
            if ((a[i] & mask) != (b[i] & mask)) return false;
        }
        return true;
    }

    /** The limited broadcast. */
    static InetAddress limitedBroadcast() {
        try {
            return InetAddress.getByName("255.255.255.255");
        } catch (Exception e) {
            return null;
        }
    }

    /** This machine's site-local IPv4 — the address a friend on the same segment knocks on. */
    public static String lanAddress() {
        for (InetAddress a : lanAddresses())
            if (a.isSiteLocalAddress()) return a.getHostAddress();
        List<InetAddress> all = lanAddresses();
        return all.isEmpty() ? "127.0.0.1" : all.get(0).getHostAddress();
    }

    /** The segment's prefix length, guessed from the address itself: the JDK only learned to
     *  ask the interface in Java 18 and this game runs on 17. Home networks are /24 almost
     *  without exception, and the private ranges are well known. */
    static int guessPrefix(InetAddress addr) {
        byte[] ip = addr.getAddress();
        if (ip.length != 4) return -1;
        int b0 = ip[0] & 0xFF, b1 = ip[1] & 0xFF;
        if (b0 == 10) return 8;                 // 10.0.0.0/8
        if (b0 == 172 && b1 >= 16 && b1 <= 31) return 16; // 172.16.0.0/12
        if (b0 == 192 && b1 == 168) return 24;  // 192.168.0.0/16
        if (b0 == 169 && b1 == 254) return 16;  // 169.254.0.0/16
        return 24;
    }

    /** The segment's own broadcast address: the host bits flipped. */
    static InetAddress subnetBroadcast(InetAddress addr, int prefix) {
        byte[] ip = addr.getAddress();
        if (ip.length != 4 || prefix < 0 || prefix > 32) return null;
        byte[] b = ip.clone();
        for (int i = 0; i < 4; i++) {
            int bits = Math.min(8, Math.max(0, prefix - i * 8));
            int mask = bits == 0 ? 0 : (0xFF << (8 - bits)) & 0xFF;
            b[i] = (byte) ((ip[i] & mask) | (~mask & 0xFF));
        }
        try {
            return InetAddress.getByAddress(b);
        } catch (Exception e) {
            return null;
        }
    }
}
