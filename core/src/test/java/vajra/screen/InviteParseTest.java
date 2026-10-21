package vajra.screen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** The invite code law: "ABCD@host:port" is a room and its relay in one string —
 *  the joiner dials the HOST's server, not the default chain. Everything else
 *  keeps reading as a room code or a LAN address, exactly as before. */
class InviteParseTest {

    @Test
    void anInviteSplitsRoomFromRelay() {
        String[] got = NetScreen.inviteOf("ABCD@192.168.1.9:27040");
        assertNotNull(got, "the plain invite shape parses");
        assertEquals("ABCD", got[0]);
        assertEquals("192.168.1.9:27040", got[1]);
    }

    @Test
    void theOldRoomShapesStillLeadTheInvite() {
        String[] prefixed = NetScreen.inviteOf("room:abcd@10.0.0.2:27040");
        assertNotNull(prefixed, "a prefixed room rides the invite too");
        assertEquals("ABCD", prefixed[0]);
        assertArrayEquals(new String[] { "KLMN", "host.example:8080" },
                NetScreen.inviteOf("klmn@host.example:8080"), "case and spaces are normalized");
    }

    @Test
    void aRoomCodeAloneIsNotAnInvite() {
        assertNull(NetScreen.inviteOf("ROOM:ABCD"), "the room-only form stays a room");
        assertEquals("ABCD", NetScreen.roomOf("ROOM:ABCD"), "and the room path keeps working");
        assertEquals("ABCD", NetScreen.roomOf("abcd"), "the bare token too");
    }

    @Test
    void brokenInvitesFallThroughLoudly() {
        assertNull(NetScreen.inviteOf("ABCD@no-port"), "a relay without a port is no invite");
        assertNull(NetScreen.inviteOf("@10.0.0.2:27040"), "no room, no invite");
        assertNull(NetScreen.inviteOf("TOOLONG@10.0.0.2:27040"), "five letters are no room");
        assertNull(NetScreen.roomOf("192.168.1.5:26000"), "an address is a LAN join, not a room");
    }

    /** The SERVER LINK row's law: a bare host rides the default 27040, host:port
     *  stands as typed, and anything with spaces or a struck port is no relay. */
    @Test
    void aServerLinkShapesToHostPort() {
        assertEquals("box.lan:27040", NetScreen.relayCheck("box.lan"), "a bare host rides 27040");
        assertEquals("10.0.0.2:6000", NetScreen.relayCheck(" 10.0.0.2:6000 "), "spacing is trimmed");
        assertNull(NetScreen.relayCheck("host 60"), "no stray words");
        assertNull(NetScreen.relayCheck("host:60abc"), "the port must be a number");
        assertNull(NetScreen.relayCheck(""));
        assertNull(NetScreen.relayCheck(null));
    }
}
