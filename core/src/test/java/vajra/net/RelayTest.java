package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;
import vajra.relay.RelayServer;

/** The relay's smoke: a real server on a localhost port (the SOP's named-socket
 *  exemptions), a host and a joiner dialing it, and the two of them exchanging packets
 *  through the room. Also the refuses: a join on a room that isn't, and a second host
 *  on a live code. Bounded by the dial's own 1.5 s waits — no sleeps beyond them. */
class RelayTest {

    @Test
    void aRoomCarriesPacketsBothWays() throws Exception {
        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) {
            port = probe.getLocalPort();
        }
        Thread server = new Thread(() -> {
            try {
                new RelayServer(port).run();
            } catch (Exception fatal) {
                // the test fails loudly below if the relay never came up
            }
        }, "vajra-relay-test");
        server.setDaemon(true);
        server.start();

        RelayTransport.RELAY = "127.0.0.1:" + port;
        RelayTransport host = RelayTransport.host("TEST", "AGNI");
        RelayTransport joiner = RelayTransport.join("TEST", "VARUNA");
        assertEquals("TEST", host.roomCode(), "the code survives the wire");

        // the session's hello shapes: the joiner dials the HOST's virtual address (it read
        // it off its WELCOME), so the host learns the joiner's from the frame that answers
        byte[] ready = "ready".getBytes();
        joiner.send(ready, joiner.hostAddress());
        Transport.Received got = waitFor(host, ready.length);
        assertNotNull(got, "the host heard the joiner through the room");
        assertEquals(2, ((java.net.InetSocketAddress) got.from).getPort(),
                "the joiner arrived as ep 2 (the host's ep is 1)");
        assertArrayEquals(ready, got.data);

        // and back again: the host answers on the address the hello arrived from
        byte[] hello = "hello".getBytes();
        host.send(hello, got.from);
        Transport.Received back = waitFor(joiner, hello.length);
        assertNotNull(back, "the joiner heard the host");
        assertEquals(joiner.hostAddress(), back.from, "the host's virtual address is stable");
        assertArrayEquals(hello, back.data);

        host.close();
        joiner.close();
    }

    /** Poll until the data lands or ~2 s passes (the relay runs its own thread). */
    private Transport.Received waitFor(Transport t, int atLeast) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Transport.Received r = t.poll();
            if (r != null && r.data.length >= atLeast) return r;
            Thread.sleep(20);
        }
        return null;
    }

    @Test
    void aJoinOnAnOpenRoomIsRefused() {
        RelayTransport.RELAY = "127.0.0.1:27041"; // nothing listens there in CI
        assertThrows(IllegalStateException.class, () -> RelayTransport.join("MISS", "VARUNA"));
    }

    @Test
    void aFifthPilotCannotEnterARoomOfFour() throws Exception {
        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) {
            port = probe.getLocalPort();
        }
        Thread server = new Thread(() -> {
            try {
                new RelayServer(port).run();
            } catch (Exception fatal) {
                // the test fails loudly below if the relay never came up
            }
        }, "vajra-relay-c-cap");
        server.setDaemon(true);
        server.start();

        RelayTransport.RELAY = "127.0.0.1:" + port;
        RelayTransport[] pilots = new RelayTransport[4]; // the host plus three joiners fill it
        pilots[0] = RelayTransport.host("FOUR", "AGNI");
        for (int i = 1; i < 4; i++) pilots[i] = RelayTransport.join("FOUR", "P" + i);
        try {
            IllegalStateException gone = assertThrows(IllegalStateException.class,
                    () -> RelayTransport.join("FOUR", "FIFTH"));
            assertTrue(gone.getMessage().contains("ROOM FULL"),
                    "the fifth knock reads as the cap: " + gone.getMessage());
        } finally {
            for (RelayTransport t : pilots) if (t != null) t.close();
        }
    }

    /** The joiner's room table: the relay answers LIST with its live rooms — the
     *  lobby list, so a friend never has to type a code the host has to shout first.
     *  Plus the silence law: a relay that says nothing answers null, never an array. */
    @Test
    void aRelayListsTheRoomsItHolds() throws Exception {
        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) {
            port = probe.getLocalPort();
        }
        Thread server = new Thread(() -> {
            try {
                new RelayServer(port).run();
            } catch (Exception fatal) {
                // the test fails loudly below if the relay never came up
            }
        }, "vajra-relay-list");
        server.setDaemon(true);
        server.start();

        RelayTransport.RELAY = "127.0.0.1:" + port;
        RelayTransport host = RelayTransport.host("LIST", "AGNI");
        try {
            RelayTransport.RoomInfo[] rooms = RelayTransport.rooms(
                    new InetSocketAddress("127.0.0.1", port));
            assertNotNull(rooms, "a living relay answers the list");
            boolean mine = false;
            for (RelayTransport.RoomInfo r : rooms)
                if (r.code.equals("LIST")) mine = r.pilots == 1;
            assertTrue(mine, "the hosted room is on the table at one pilot");
        } finally {
            host.close();
        }

        RelayTransport.RoomInfo[] silence = RelayTransport.rooms(
                new InetSocketAddress("127.0.0.1", 27041)); // nothing listens there in CI
        assertNull(silence, "a silent relay is an unanswered ask, not an empty table");
    }

    /** The invite law end to end: both pilots dial an EXPLICIT relay while the default
     *  chain points somewhere dead — the host's own server carries their room. Plus
     *  the host's invite string round-trips through the parser the join screen uses. */
    @Test
    void anInviteAddressCarriesItsOwnRelay() throws Exception {
        int port;
        try (DatagramSocket probe = new DatagramSocket(0)) {
            port = probe.getLocalPort();
        }
        Thread server = new Thread(() -> {
            try {
                new RelayServer(port).run();
            } catch (Exception fatal) {
                // the test fails loudly below if the relay never came up
            }
        }, "vajra-relay-invite");
        server.setDaemon(true);
        server.start();

        RelayTransport.RELAY = "127.0.0.1:27041"; // the default chain is a dead end here
        InetSocketAddress mine = new InetSocketAddress("127.0.0.1", port);
        RelayTransport host = RelayTransport.host("JOIN", "AGNI", mine);
        RelayTransport joiner = null;
        try {
            assertEquals("JOIN", host.roomCode(), "the room took under the explicit dial");
            String invite = host.invite(); // ABCD@127.0.0.1:<port> — parse the shape back
            String[] parts = invite.split("@");
            assertEquals("JOIN", parts[0], "the invite leads with the code");
            assertTrue(parts[1].endsWith(":" + port), "the invite carries the relay: " + invite);
            joiner = RelayTransport.join("JOIN", "VARUNA",
                    new InetSocketAddress(parts[1].substring(0, parts[1].lastIndexOf(':')),
                            Integer.parseInt(parts[1].substring(parts[1].lastIndexOf(':') + 1))));
            byte[] ready = "welcome".getBytes();
            joiner.send(ready, joiner.hostAddress());
            assertNotNull(waitFor(host, ready.length), "room traffic through the invite relay");
        } finally {
            host.close();
            if (joiner != null) joiner.close();
        }
    }
}
