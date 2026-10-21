package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

/** The no-hassle connection's parsers: the LAN beacon a host shouts, the STUN address a
 *  peer learns, the UPnP control URL the door opener reads, and the invite the lobby prints.
 *  All of them are pure, so they are pinned here rather than on a real network. */
class NetPlayTest {

    // ---- the beacon ---------------------------------------------------------------------------

    @Test
    void aBeaconCarriesTheRoomTheNameAndThePort() {
        byte[] bytes = LanBeacon.beacon("ABCD", "AGNI", 26000);
        DatagramPacket p = new DatagramPacket(bytes, bytes.length,
                new InetSocketAddress("192.168.1.9", 9999));
        LanBeacon.Found f = LanBeacon.read(p);
        assertNotNull(f, "the beacon reads back");
        assertEquals("ABCD", f.room);
        assertEquals("AGNI", f.name);
        assertEquals(26000, f.addr.getPort(), "the port the friend knocks on");
        assertEquals("192.168.1.9", f.addr.getAddress().getHostAddress());
        assertTrue(f.label().contains("ABCD"), "the label shows the room: " + f.label());
    }

    @Test
    void junkIsNotABeacon() {
        byte[] junk = "not a beacon at all, really not".getBytes();
        assertNull(LanBeacon.read(new DatagramPacket(junk, junk.length,
                new InetSocketAddress("10.0.0.1", 1))));
        byte[] short1 = new byte[] { 'V', 'J' };
        assertNull(LanBeacon.read(new DatagramPacket(short1, short1.length,
                new InetSocketAddress("10.0.0.1", 1))));
    }

    @Test
    void theSegmentBroadcastIsTheHostBitsFlipped() throws Exception {
        assertEquals("192.168.1.255", LanBeacon.subnetBroadcast(
                InetAddress.getByName("192.168.1.5"), 24).getHostAddress());
        assertEquals("10.255.255.255", LanBeacon.subnetBroadcast(
                InetAddress.getByName("10.0.0.3"), 8).getHostAddress());
        assertEquals("172.16.255.255", LanBeacon.subnetBroadcast(
                InetAddress.getByName("172.16.4.9"), 16).getHostAddress());
    }

    @Test
    void thePrefixIsGuessedFromTheAddress() throws Exception {
        assertEquals(24, LanBeacon.guessPrefix(InetAddress.getByName("192.168.7.7")));
        assertEquals(16, LanBeacon.guessPrefix(InetAddress.getByName("172.20.1.1")));
        assertEquals(8, LanBeacon.guessPrefix(InetAddress.getByName("10.1.2.3")));
    }

    // ---- the door -----------------------------------------------------------------------------

    /** A STUN binding response: the XOR flavour hides the address behind the cookie. */
    @Test
    void theStunAddressIsReadOutOfTheResponse() {
        int magic = 0x2112A442;
        byte[] d = new byte[20 + 12];
        d[0] = 0x01;
        d[1] = 0x01; // a binding success
        d[4] = (byte) (magic >> 24);
        d[5] = (byte) (magic >> 16);
        d[6] = (byte) (magic >> 8);
        d[7] = (byte) magic;
        // the attribute header: XOR-MAPPED-ADDRESS, eight bytes
        d[20] = 0x00;
        d[21] = 0x20;
        d[22] = 0x00;
        d[23] = 0x08;
        d[24] = 0x00;
        d[25] = 0x01; // IPv4
        int port = 41234 ^ (magic >> 16);
        d[26] = (byte) (port >> 8);
        d[27] = (byte) port;
        int ip = 0xCB00715E ^ magic; // 203.0.113.94
        d[28] = (byte) (ip >> 24);
        d[29] = (byte) (ip >> 16);
        d[30] = (byte) (ip >> 8);
        d[31] = (byte) ip;
        String[] a = Nat.stunAddress(d, d.length, magic);
        assertNotNull(a, "the response carries an address");
        assertEquals("203.0.113.94", a[0]);
        assertEquals("41234", a[1]);
    }

    /** The plain MAPPED-ADDRESS flavour reads without the cookie. */
    @Test
    void thePlainStunAddressIsReadToo() {
        byte[] d = new byte[20 + 12];
        d[0] = 0x01;
        d[1] = 0x01;
        d[20] = 0x00;
        d[21] = 0x01; // MAPPED-ADDRESS
        d[22] = 0x00;
        d[23] = 0x08;
        d[24] = 0x00;
        d[25] = 0x01;
        d[26] = (byte) (54321 >> 8);
        d[27] = (byte) 54321;
        d[28] = (byte) 203;
        d[29] = 0;
        d[30] = 113;
        d[31] = 7;
        String[] a = Nat.stunAddress(d, d.length, 0x2112A442);
        assertNotNull(a);
        assertEquals("203.0.113.7", a[0]);
        assertEquals("54321", a[1]);
    }

    @Test
    void theUpnpControlUrlIsFoundInTheDeviceDescription() {
        String xml = "<root><device><serviceList><service>"
                + "<serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>"
                + "<controlURL>/ctl/IPConn</controlURL>"
                + "</service></serviceList></device></root>";
        assertEquals("/ctl/IPConn", Nat.controlUrl(xml));
        assertNull(Nat.controlUrl("<root><device/></root>"), "a device with no WAN service");
    }

    @Test
    void aLocationSplitsIntoItsHostAndPort() {
        Nat.Gateway gw = Nat.fromLocation("http://192.168.1.1:5000/rootDesc.xml");
        assertNotNull(gw);
        assertEquals("192.168.1.1", gw.host);
        assertEquals(5000, gw.port);
        assertNull(Nat.fromLocation("not a url"));
    }

    // ---- the invite ----------------------------------------------------------------------------

    /** The one link a host shares is its public address, which a pilot on the host's own
     *  network may not be able to reach. The room shouts its LAN address on the wire, so
     *  the joiner looks the room up BY ITS FOUR LETTERS and knocks there instead — one
     *  link, either kind of network. */
    @Test
    void aRoomIsFoundByItsFourLettersOnTheNetwork() throws Exception {
        LanBeacon.Host beacon = new LanBeacon.Host("WXYZ", "KALA", 26000);
        LanBeacon.Ear ear = new LanBeacon.Ear();
        try {
            LanBeacon.Found heard = null;
            long until = System.currentTimeMillis() + 4000;
            while (heard == null && System.currentTimeMillis() < until) {
                for (LanBeacon.Found f : ear.rooms()) if (f.room.equals("WXYZ")) heard = f;
                if (heard == null) Thread.sleep(50);
            }
            assertNotNull(heard, "the room was heard");
            assertNotNull(ear.room("WXYZ"), "and it is found by its own code");
            assertNotNull(ear.room("wxyz"), "case does not matter");
            assertNull(ear.room("ZZZZ"), "a code nobody shouted is nobody's room");
        } finally {
            beacon.close();
            ear.close();
        }
    }

    /** The invite the lobby prints: one link, whichever door answered. */
    @Test
    void theInviteCarriesTheRoomAndTheDoor() {
        String old = Net.room;
        try {
            Net.room = "ABCD";
            Net.door = new Nat.Result("203.0.113.9", 26000, true, "UPnP", "OPENED");
            Net.doorPort = 26000;
            assertEquals("ABCD@203.0.113.9:26000", Net.invite(26000),
                    "the public address when the door opened");
            Net.door = null;
            Net.doorPort = 0;
            String invite = Net.invite(26000);
            assertTrue(invite.startsWith("ABCD@"), "the room leads: " + invite);
            assertTrue(invite.endsWith(":26000"), "and the game's own port ends it: " + invite);
        } finally {
            Net.room = old;
            Net.door = null;
            Net.doorPort = 0;
        }
    }
}
