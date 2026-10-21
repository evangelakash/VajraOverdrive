package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/** The real wire, smoked once: two UDP transports on localhost trade a packet each way.
 *  This is the only test that touches actual sockets — everything else runs on loopback. */
class UdpTransportTest {

    @Test
    void localhostTrade() throws Exception {
        UdpTransport a = new UdpTransport(0);
        UdpTransport b = new UdpTransport(0);
        InetSocketAddress aAddr = new InetSocketAddress("127.0.0.1", a.port());
        InetSocketAddress bAddr = new InetSocketAddress("127.0.0.1", b.port());
        assertNotEquals(a.port(), b.port());

        a.send("A→B".getBytes("UTF-8"), bAddr);
        b.send("B→A".getBytes("UTF-8"), aAddr);

        Transport.Received ab = null, ba = null;
        for (int i = 0; i < 40 && (ab == null || ba == null); i++) { // ≤ 2 s, then fail loudly
            if (ab == null) ab = a.poll();
            if (ba == null) ba = b.poll();
            if (ab == null || ba == null) Thread.sleep(50);
        }
        assertNotNull(ab, "B's packet reached A");
        assertNotNull(ba, "A's packet reached B");
        assertEquals("B→A", new String(ab.data, "UTF-8"));
        assertEquals("A→B", new String(ba.data, "UTF-8"));

        a.close();
        b.close();
        a.send("after close".getBytes("UTF-8"), bAddr); // dropped silently, no exception
        assertNull(a.poll());
    }
}
