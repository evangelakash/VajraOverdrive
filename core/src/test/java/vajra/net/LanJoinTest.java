package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/** The whole no-hassle path, over real sockets: a host shouts its room, an ear hears it,
 *  and the friend joins by knocking on the address the shout carried. No typing, no setup
 *  file, no second program. */
class LanJoinTest {

    /** The ear hears a beacon and the friend joins the room it names — the whole point of
     *  the shout: no address is typed, none is read off a screen. */
    @Test
    void aFriendHearsTheRoomAndJoinsIt() throws Exception {
        UdpTransport hostNet = new UdpTransport(UdpTransport.PORT);
        Session host = Session.host(hostNet, "AGNI");
        LanBeacon.Ear ear = new LanBeacon.Ear();
        try {
            // the shout, as the wire carries it: one datagram to the ear's ports
            byte[] bytes = LanBeacon.beacon("ABCD", "AGNI", UdpTransport.PORT);
            DatagramSocket shout = new DatagramSocket();
            for (int port : new int[] { LanBeacon.PORT, LanBeacon.SIDE_PORT })
                shout.send(new DatagramPacket(bytes, bytes.length,
                        new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)));
            shout.close();

            LanBeacon.Found heard = null;
            long until = System.currentTimeMillis() + 3000;
            while (heard == null && System.currentTimeMillis() < until) {
                if (!ear.rooms().isEmpty()) heard = ear.rooms().get(0);
                else Thread.sleep(20);
            }
            assertNotNull(heard, "the ear heard the room");
            assertEquals("ABCD", heard.room);
            assertEquals("AGNI", heard.name);

            // the friend knocks on the address the shout carried — no typing anywhere
            UdpTransport clientNet = new UdpTransport(0);
            Session client = Session.join(clientNet, "VARUNA", heard.addr);
            long joinUntil = System.currentTimeMillis() + 4000;
            while (client.state() != Session.State.LIVE && System.currentTimeMillis() < joinUntil) {
                host.update();
                client.update();
                Thread.sleep(10);
            }
            assertEquals(Session.State.LIVE, client.state(), "the room opened: guest="
                    + client.state());
            assertEquals(Session.State.LIVE, host.state(), "and the host sees it too: "
                    + host.state());
            assertEquals(2, host.count(), "both pilots are on the roster");
            assertEquals("VARUNA", host.member(1).name);
            client.close();
        } finally {
            ear.close();
            hostNet.close();
        }
    }

    /** The host's own shout reaches the ear — the real broadcast, on this machine. */
    @Test
    void theHostsShoutIsHeardOnTheLan() throws Exception {
        LanBeacon.Host beacon = new LanBeacon.Host("WXYZ", "KALA", UdpTransport.PORT);
        LanBeacon.Ear ear = new LanBeacon.Ear();
        try {
            LanBeacon.Found heard = null;
            long until = System.currentTimeMillis() + 4000;
            while (heard == null && System.currentTimeMillis() < until) {
                for (LanBeacon.Found f : ear.rooms()) if (f.room.equals("WXYZ")) heard = f;
                if (heard == null) Thread.sleep(50);
            }
            assertNotNull(heard, "the broadcast reached the ear on this machine");
            assertEquals("KALA", heard.name);
            assertEquals(UdpTransport.PORT, heard.addr.getPort());
        } finally {
            beacon.close();
            ear.close();
        }
    }

    /** The whole chain, over real sockets: a room is found, both pilots ready up, the host
     *  locks ONE seed, and both desks hand off into the same run. This is the gate that the
     *  connection produces a game — not just a socket. */
    @Test
    void aJoinedRoomLocksOneSeedAndBothFlyIt() throws Exception {
        UdpTransport hostNet = new UdpTransport(UdpTransport.PORT);
        Session host = Session.host(hostNet, "AGNI");
        LanBeacon.Ear ear = new LanBeacon.Ear();
        try {
            byte[] bytes = LanBeacon.beacon("ABCD", "AGNI", UdpTransport.PORT);
            DatagramSocket shout = new DatagramSocket();
            for (int port : new int[] { LanBeacon.PORT, LanBeacon.SIDE_PORT })
                shout.send(new DatagramPacket(bytes, bytes.length,
                        new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)));
            shout.close();

            LanBeacon.Found heard = null;
            long heardUntil = System.currentTimeMillis() + 3000;
            while (heard == null && System.currentTimeMillis() < heardUntil) {
                if (!ear.rooms().isEmpty()) heard = ear.rooms().get(0);
                else Thread.sleep(20);
            }
            assertNotNull(heard, "the room was heard");

            UdpTransport clientNet = new UdpTransport(0);
            Session client = Session.join(clientNet, "VARUNA", heard.addr);
            long joinUntil = System.currentTimeMillis() + 4000;
            while (client.state() != Session.State.LIVE && System.currentTimeMillis() < joinUntil) {
                host.update();
                client.update();
                Thread.sleep(10);
            }
            assertEquals(Session.State.LIVE, client.state());

            client.toggleReady();
            host.toggleReady();
            long readyUntil = System.currentTimeMillis() + 2000;
            while (host.count() > 0 && System.currentTimeMillis() < readyUntil) {
                host.update();
                client.update();
                // the hub's own roster must show the guest ready, not just the guest's flag
                if (host.ready() && client.ready() && host.count() > 1
                        && host.member(1).ready) break;
                Thread.sleep(10);
            }
            host.start();
            for (int i = 0; i < 20; i++) { // the START goes out three times; let them land
                host.update();
                client.update();
                Thread.sleep(10);
            }
            assertEquals(Session.State.HANDOFF, host.state(), "the seed locked: " + host.state());
            assertEquals(Session.State.HANDOFF, client.state(), "and the guest got it: "
                    + client.state());
            assertEquals(host.seed(), client.seed(), "ONE seed: both desks fly the same run");
            assertTrue(host.seed() != 0, "and it is a real seed");

            host.enterRun();
            client.enterRun();
            assertEquals(Session.State.RUN, client.state());
            assertEquals(Session.State.RUN, host.state());
            client.close();
        } finally {
            ear.close();
            hostNet.close();
        }
    }
}
