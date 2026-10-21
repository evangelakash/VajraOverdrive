package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

/** The ping law: a client's PING echoes the host's roster stamp home, so the HOST measures
 *  its round trip to every guest; the host's unchanged echo of the client's own stamp gives
 *  the guest its own read. Both sides' numbers ride back to everyone on the roster. Wall
 *  clock based (the measure itself is real time), so sleeps join the loop like RelayTest. */
class SessionPingTest {

    private static final SocketAddress ANY = new java.net.InetSocketAddress(26000);

    @Test
    void pingsReachBothSidesAndRideTheRoster() throws Exception {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session guest = Session.join(b, "VARUNA", ANY);

        pump(host, guest);                       // the handshake lands
        assertEquals(Session.State.LIVE, guest.state(), "the roster handshake completed");
        Thread.sleep(2200);                      // past the 2 s beat: the guest's first PING goes
        pump(host, guest);
        Thread.sleep(2200);                      // and the next one, stamp in hand
        pump(host, guest);

        int mine = guest.pingOf(guest.myId());
        assertTrue(mine > 0 && mine < 5000, "the guest measured its own RTT, got " + mine);
        int theirs = host.pingOf(1);
        assertTrue(theirs > 0 && theirs < 5000, "the host measured its RTT to the guest, got " + theirs);

        // the guest's mirror sees the host's own measure for it via the roster,
        // unless its own fresher read already filled the slot — either way a number rides up
        int mirrored = 0;
        for (int i = 0; i < guest.count(); i++)
            if (guest.member(i).id == guest.myId()) mirrored = guest.member(i).ping;
        assertTrue(mirrored > 0, "the roster shares the host's measure back to the guest");
    }

    @Test
    void anUnmeasuredRowReadsAsZero() {
        LoopbackTransport a = new LoopbackTransport();
        Session host = Session.host(a, "AGNI");
        assertEquals(0, host.pingOf(3), "silence measures as silence");
        assertEquals(0, host.pingOf(0), "the host has no delay to itself");
    }

    private static void pump(Session host, Session guest) {
        for (int i = 0; i < 24; i++) { host.update(); guest.update(); }
    }
}
