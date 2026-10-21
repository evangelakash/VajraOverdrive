package vajra.net;

import com.badlogic.gdx.utils.Json;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** The control-plane vocabulary: ONE JSON envelope per control packet, tagged on the wire
 *  by byte 0 of the datagram (tag 0 = control, 1 = voice — see {@link Session}). One
 *  envelope class keeps the protocol to one serializer and one tolerant reader. Older or
 *  stranger clients are understood, not rejected: unknown JSON keys are skipped. */
public final class Msg {
    public static final String HELLO = "HELLO";     // client -> host: "here I am, name is ..."
    public static final String WELCOME = "WELCOME"; // host -> the new client: your id + the roster
    public static final String ROSTER = "ROSTER";   // host -> all: the truth, doubling as heartbeat
    public static final String READY = "READY";     // client -> host: my ready flag flipped
    public static final String START = "START";     // host -> all: the run seed is locked
    public static final String RESULT = "RESULT";   // a pilot -> host: their flight's score
    public static final String PING = "PING";       // client -> host: keep-alive (and NAT pin)
    public static final String BYE = "BYE";         // client -> host: leaving cleanly

    public String t;
    public int id;                    // sender's id (or the id the host assigns them)
    public String name;               // HELLO
    public boolean flag;              // READY
    public long num;                  // START: the run seed; ROSTER: the host's clock stamp
    public int rtt;                   // PING: the sender's own measured round trip to the hub, ms
    public long stamp;                // PING: the echoed host stamp the client reports
    public ArrayList<Member> members; // WELCOME / ROSTER

    /** One pilot on the roster, as the wire sees them. Everything else about a pilot
     *  (settings, career) lives in their own profile on their own machine. */
    public static class Member {
        public int id;
        public String name;
        public boolean ready;
        public long points; // this session's best flight, from the RESULT packets
        public int ping;    // the host's measured round-trip to this pilot, ms (0 = unknown)

        public Member() {}

        public Member(int id, String name, boolean ready) {
            this.id = id;
            this.name = name;
            this.ready = ready;
        }
    }

    private static final Json JSON = new Json();
    static { JSON.setIgnoreUnknownFields(true); } // a foreign version reads as best it can

    public static byte[] pack(Msg m) {
        byte[] json = JSON.toJson(m).getBytes(StandardCharsets.UTF_8);
        byte[] packet = new byte[json.length + 1];
        packet[0] = 0; // control-plane tag
        System.arraycopy(json, 0, packet, 1, json.length);
        return packet;
    }

    /** Parse a control packet; a corrupt one reads as null and the caller drops it. */
    public static Msg unpack(byte[] packet) {
        try {
            return JSON.fromJson(Msg.class,
                    new String(packet, 1, packet.length - 1, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
