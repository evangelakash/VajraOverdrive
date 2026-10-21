package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vajra.voice.VoiceChannel;

/** The session protocol, walked end to end over a paired LoopbackTransport: join,
 *  roster convergence, ready flips, the seed handshake, a dropout, seat recycling, and a
 *  corrupt packet that must be swallowed. No sockets, no sleeps — the fake clock drives the beats. */
class SessionTest {
    private static final long T0 = 1_000;
    private static final InetSocketAddress ANY = new InetSocketAddress(26000);
    /** The hub's address on the four-seat Bus below — the two-seat loopback cannot churn seats. */
    private static final InetSocketAddress HUB = new InetSocketAddress("127.0.0.1", 26000);

    @Test
    void theWholeHandshakeEndsInHandoff() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);

        host.update(T0);            // host answers the hello
        client.update(T0);          // client reads the welcome
        host.update(T0);            // host's first roster beat reaches everyone

        assertEquals(Session.State.LIVE, client.state(), "the client is on the roster");
        assertEquals(2, host.count());
        assertEquals(2, client.count());
        assertEquals("AGNI", host.member(0).name);
        assertEquals("VARUNA", host.member(1).name);
        assertEquals(1, client.myId(), "the host assigned ids");
        assertFalse(host.ready());
        assertFalse(client.ready());

        client.toggleReady();       // a flip travels client -> host -> roster -> mirror
        host.update(T0);
        client.update(T0);
        assertTrue(host.member(1).ready, "the host saw the flip");
        assertTrue(client.ready(), "the mirror reconciled");

        host.toggleReady();         // everyone ready; the seed can lock
        host.start();
        assertEquals(Session.State.HANDOFF, host.state());
        client.update(T0);
        assertEquals(Session.State.HANDOFF, client.state(), "START landed despite UDP loss");
        assertEquals(host.seed(), client.seed(), "everyone flies the same seed");
        assertTrue(host.seed() != 0, "the seed is real, never zero (the dedupe leans on that)");
    }

    @Test
    void aSilentPilotIsSwept() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);
        host.update(T0); // absorbs the client's first PING: its liveness clock now stands at T0

        host.update(T0 + Session.TIMEOUT + 1); // the sweep runs on the next heartbeat

        assertEquals(1, host.count(), "the silent pilot went overboard");
        assertEquals("PILOT DROPPED", host.status());
    }

    @Test
    void aCleanByeRemovesThePilotAtOnce() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);
        host.update(T0);

        client.leave();
        host.update(T0);

        assertEquals(1, host.count(), "BYE dropped the pilot without waiting for the sweep");
        assertEquals("PILOT LEFT", host.status());
    }

    @Test
    void aCorruptPacketIsDroppedNotFatal() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);

        host.update(T0);
        client.update(T0);
        b.send(new byte[] {0, 'n', 'o', 't', ' ', 'j', 's', 'o', 'n'}, ANY); // tag 0, garbage body
        b.send(new byte[0], ANY);      // empty
        b.send(new byte[1], ANY);      // tag alone
        client.update(T0);

        assertEquals(Session.State.LIVE, client.state(), "garbage never kills the session");
        assertEquals(2, client.count());
    }

    @Test
    void startRefusesUnreadyButTheReadyHostStartsAlone() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);

        host.start(); // client not ready
        assertNotEquals(Session.State.HANDOFF, host.state(),
                "an unready roster cannot lock a seed");

        client.leave();
        host.update(T0); // the sweep reads the BYE: the host is alone now
        host.toggleReady(); // alone but ready: the host flies the solo run
        host.start();
        assertEquals(Session.State.HANDOFF, host.state(), "a ready solo host locks a seed");
        assertTrue(host.seed() != 0, "the solo lock still hands out a seed");
    }

    @Test
    void voiceFramesRideTheSameWireAndSkipYourself() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);

        client.voice.setMyId(1);
        byte[] frame = new byte[2 + VoiceChannel.FRAME_SAMPLES]; // [tag][senderId][payload]
        frame[0] = 1; // the wire's voice tag
        frame[1] = 1; // the sender's roster id
        for (int i = 2; i < frame.length; i++) frame[i] = 0x40; // strong μ-law cells: audible
        b.send(frame, ANY); // as if VoiceChannel had handed one to the session for the wire
        host.update(T0);

        assertTrue(host.voice.talkingOf(1), "the host hears the client talking");
        assertFalse(host.voice.talkingOf(0), "nobody relays your own voice back at you");
    }

    @Test
    void aRaceRunsScoresAndFeedsEveryone() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);
        client.toggleReady();
        host.update(T0);
        host.toggleReady();
        host.start();
        client.update(T0); // START + the roster that reset the flags

        assertEquals(Session.State.HANDOFF, client.state());
        assertFalse(client.ready(), "ready flags reset with a new race");
        assertFalse(host.ready());

        host.enterRun();    // the lobby hands off on both machines
        client.enterRun();
        assertEquals(Session.State.RUN, host.state());
        assertEquals(Session.State.RUN, client.state());
        assertEquals(host.seed(), client.seed());

        client.report(500); // the client's flight is over: its number flies to the hub
        client.report(300); // a worse re-fly can only lose to the book
        host.report(700);   // the host's own flight lands straight in the book
        host.update(T0);
        client.update(T0);

        assertEquals(500, host.member(1).points, "the book keeps the best flight");
        assertEquals(700, client.member(0).points, "the mirror sees the host's number");
        assertEquals(700, host.member(0).points);
        assertEquals(500, client.member(1).points);

        host.backToLobby(); // the host walked out first...
        assertEquals(Session.State.LIVE, host.state());
        client.backToLobby(); // ...the client follows
        assertEquals(Session.State.LIVE, client.state());
    }

    @Test
    void aStartCannotRestartOverFlyingPilots() {
        LoopbackTransport a = new LoopbackTransport(), b = new LoopbackTransport();
        LoopbackTransport.pair(a, b);
        Session host = Session.host(a, "AGNI");
        Session client = Session.join(b, "VARUNA", ANY);
        host.update(T0);
        client.update(T0);
        client.toggleReady();
        host.update(T0);
        host.toggleReady();
        host.start();
        client.update(T0);

        host.backToLobby(); // the host returns but the client is still flying
        long oldSeed = host.seed();
        host.start(); // refused while the client is in RUN (and unready on top of it)
        assertEquals(oldSeed, host.seed(), "the seed did not reboot mid-race");
    }

    // ---- a four-seat wire, routed by address: the loopback is strictly two --------------------

    /** One seat's endpoint on a shared bus, every send routed by address — so a hub can hold
     *  four seats and a fifth pilot can arrive after a fourth walks out. That churn is what
     *  the two-seat LoopbackTransport can never produce. */
    private static final class Bus implements Transport {
        static final Map<SocketAddress, Bus> REGISTRY = new HashMap<>();
        final ArrayDeque<Received> in = new ArrayDeque<>();
        final SocketAddress addr;

        Bus(int port) {
            addr = new InetSocketAddress("127.0.0.1", port);
            REGISTRY.put(addr, this);
        }

        @Override
        public void send(byte[] data, SocketAddress to) {
            Bus dst = REGISTRY.get(to);
            if (dst != null) dst.in.add(new Received(data, addr));
        }

        @Override
        public Received poll() { return in.pollFirst(); }

        @Override
        public void close() { in.clear(); }
    }

    @Test
    void aFreedSeatIsReusedSoNoTwoPilotsWearTheSameColour() {
        Bus.REGISTRY.clear();
        Bus hub = new Bus(26000);
        Session host = Session.host(hub, "AGNI");
        Session[] guests = new Session[3];
        for (int i = 0; i < 3; i++) {
            guests[i] = Session.join(new Bus(26001 + i), "PILOT" + (i + 1), HUB);
            meet(host, guests[i]);
            assertEquals(i + 1, guests[i].myId(), "seats are handed out in join order");
        }
        assertEquals(4, host.count(), "the room is full: the host plus three");
        everySeatStaysInsideTheFourColours(host);

        guests[0].leave(); // BYE: seat 1 is free the moment the host reads it
        host.update(T0);
        assertEquals(3, host.count(), "the departed pilot's seat went back in the pool");

        Session late = Session.join(new Bus(26004), "INDRA", HUB);
        meet(host, late);
        assertEquals(4, host.count());
        assertEquals(1, late.myId(),
                "the freed seat is reused — a fresh id past 3 would repaint the host's pink");
        everySeatStaysInsideTheFourColours(host);
    }

    /** One handshake's worth of turns: hello, welcome, first roster. */
    private static void meet(Session host, Session guest) {
        host.update(T0);
        guest.update(T0);
        host.update(T0);
    }

    /** Every id sits below the roster cap and no two seats share one — which is exactly
     *  the guarantee SeatColors needs, since it picks its hue by {@code id & 3}. */
    private static void everySeatStaysInsideTheFourColours(Session host) {
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i = 0; i < host.count(); i++) {
            int id = host.member(i).id;
            assertTrue(id < Session.MAX, "seat " + id + " is outside the four seat colours");
            assertTrue(seen.add(id), "two pilots are sharing seat id " + id);
        }
    }
}
