package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

/** The LAN fixes, each pinned where it broke: the per-interface beacon socket that never
 *  bound, the real broadcast address, a host on several networks listed once, a knock that
 *  retries inside the join window, and game traffic that keeps a pilot on the roster. */
class LanFixesTest {
    private static final InetSocketAddress ANY = new InetSocketAddress(26000);
    private static final long T0 = 1_000;

    @Test
    void theBeaconSocketReallyBindsToTheInterface() throws Exception {
        InetAddress lo = InetAddress.getLoopbackAddress();
        DatagramSocket s = LanBeacon.boundSocket(lo);
        assertNotNull(s, "new DatagramSocket() is born bound, so bind() always threw before");
        try {
            assertEquals(lo, s.getLocalAddress(), "and it speaks from that interface's address");
            assertTrue(s.getBroadcast());
        } finally {
            s.close();
        }
    }

    @Test
    void everyInterfaceCarriesItsOwnBroadcastAndPrefix() {
        for (LanBeacon.Lan lan : LanBeacon.lanNets()) {
            assertTrue(lan.prefix > 0 && lan.prefix <= 32, "a real prefix: " + lan.prefix);
            assertNotNull(lan.broadcast, "and a broadcast address for " + lan.ip);
            assertTrue(LanBeacon.sameNet(lan.ip.getAddress(), lan.broadcast.getAddress(), lan.prefix),
                    "the broadcast sits on the interface's own segment");
        }
    }

    @Test
    void sameNetComparesTheLeadingBits() throws Exception {
        byte[] a = InetAddress.getByName("192.168.1.4").getAddress();
        assertTrue(LanBeacon.sameNet(a, InetAddress.getByName("192.168.1.200").getAddress(), 24));
        assertFalse(LanBeacon.sameNet(a, InetAddress.getByName("192.168.2.4").getAddress(), 24));
        assertTrue(LanBeacon.sameNet(a, InetAddress.getByName("192.168.2.4").getAddress(), 16));
        assertTrue(LanBeacon.sameNet(a, InetAddress.getByName("192.168.3.4").getAddress(), 22),
                "a /22 spans four /24s - the old /24 guess missed these");
    }

    @Test
    void aHostOnSeveralNetworksIsListedOnceAndKnocksInOrder() throws Exception {
        LanBeacon.Ear ear = new LanBeacon.Ear();
        try {
            InetAddress dead = InetAddress.getByName("203.0.113.9"); // not on any segment of ours
            InetAddress mine = LanBeacon.lanNets().isEmpty() ? InetAddress.getLoopbackAddress()
                    : LanBeacon.lanNets().get(0).ip;
            long now = System.currentTimeMillis();
            ear.hear(new LanBeacon.Found(new InetSocketAddress(mine, 26000), "ABCD", "AGNI", now - 10));
            ear.hear(new LanBeacon.Found(new InetSocketAddress(dead, 26000), "ABCD", "AGNI", now));
            assertEquals(1, ear.rooms().size(), "one room, not one row per address it shouted from");
            assertEquals(2, ear.candidates("abcd").size(), "but both addresses stay knockable");
            if (LanBeacon.onMySegment(mine))
                assertEquals(mine, ear.room("ABCD").addr.getAddress(),
                        "the address on a segment we share leads, though it is not the newest");
        } finally {
            ear.close();
        }
    }

    @Test
    void aKnockRetriesInsideTheJoinWindow() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session client = Session.join(b, "VARUNA", ANY); // one HELLO already out
        int hellos = 0;
        for (long t = T0; t < T0 + 2000; t += 50) client.update(t);
        Transport.Received r;
        while ((r = a.poll()) != null) {
            Msg m = Msg.unpack(r.data);
            if (m != null && Msg.HELLO.equals(m.t)) hellos++;
        }
        assertTrue(hellos >= 5, "a lost datagram must not cost 2 s: " + hellos + " hellos in 2 s");
    }

    @Test
    void gameTrafficKeepsAPilotOnTheRoster() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);
        host.update(T0);
        assertEquals(2, host.count());
        long t = T0;
        // no PING ever again - only the 30 Hz SHIP plane, as in a run - for twice the timeout
        for (int i = 0; i < 2 * Session.TIMEOUT / 100; i++) {
            t += 100;
            client.sendPlane(2, new byte[] { 2, (byte) client.myId(), 0, 0 });
            host.update(t);
            while (b.poll() != null) { /* the guest hears nothing it needs */ }
        }
        assertEquals(2, host.count(), "a pilot who is flying is not silent");
    }
}
