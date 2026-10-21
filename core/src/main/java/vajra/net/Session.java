package vajra.net;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import vajra.voice.VoiceChannel;

/** A multiplayer session: the roster of pilots, who is ready, and the one run seed. The
 *  host is the hub — every client packet and every voice frame flows client → host →
 *  out. One transport, polled from {@link #update()} each frame; beyond the transport's
 *  own receiver there are no threads. Two sessions over a paired {@link LoopbackTransport}
 *  is how the headless tests walk the whole handshake.
 *
 *  <p>Reliability leans on repetition, not ACKs: JOINING clients re-send HELLO every
 *  heartbeat, the host re-broadcasts the ROSTER as its heartbeat, START goes out three
 *  times. Losing one datagram costs two seconds, never a state machine. */
public final class Session {
    public enum State {
        /** Host waiting for pilots. */
        OPEN,
        /** Client knocking; HELLO retries every heartbeat. */
        JOINING,
        /** On the roster and connected — the lobby's steady state. */
        LIVE,
        /** START landed: the seed is locked, the run handoff is next. */
        HANDOFF,
        /** A shadow race is being flown; the roster holds, the lobby waits. */
        RUN,
        /** Dead or gone — the screens show the status and leave. */
        LOST
    }

    public static final int MAX = 4;          // roster cap: four pilots, four seat colours
    public static final int HEARTBEAT = 2000; // roster broadcast / ping period, ms
    public static final int TIMEOUT = 6000;   // silence that loses a pilot, ms
    /** A knocking client re-sends HELLO this often. Wi-Fi drops datagrams in bursts, and the
     *  2 s heartbeat alone left a knock only two or three tries inside the join window. */
    static final int KNOCK_RETRY = 400;

    private final Transport net;
    private final String myName;
    private final SocketAddress hubAddr;                       // client only; null = I am the hub
    private final ArrayList<Msg.Member> members = new ArrayList<>(); // host: authoritative; client: the mirror
    private final HashMap<Integer, Long> lastSeen = new HashMap<>(); // host: per member; client: [myId] = the host
    private final ConcurrentHashMap<Integer, SocketAddress> addrOf = new ConcurrentHashMap<>();
    private int myId = -1;
    private State state;
    private long seed;
    private long nextBeat;
    private long nextKnock;             // the next HELLO retry while JOINING
    private long joinedAt = -1;          // a JOINING knocker gives up if no WELCOME ever lands
    private long rosterStamp;           // the host's clock stamp that rode the last roster out
    private long[] myStamps = {-1, -1, -1, -1}; // the guest's last sent ping stamps (echo ring)
    private int stampAt;
    private final HashMap<Integer, Float> pings = new HashMap<>(); // smoothed RTT, ms
    private long epoch;                 // bumps when the crew list's numbers move (the HUD caches on it)
    private String status = "";

    /** The voice channel rides every session and routes its frames through it. */
    public final VoiceChannel voice;

    public static Session host(Transport net, String name) {
        Session s = new Session(net, name, null);
        s.members.add(new Msg.Member(0, name, false));
        s.state = State.OPEN;
        return s;
    }

    public static Session join(Transport net, String name, SocketAddress hostAddr) {
        Session s = new Session(net, name, hostAddr);
        s.state = State.JOINING;
        s.joinedAt = -1; // stamped on the first pump, so the test clock owns the give-up
        s.sayHello(); // ask at once; the heartbeat retries until answered
        return s;
    }

    private Session(Transport net, String name, SocketAddress hubAddr) {
        this.net = net;
        this.myName = name;
        this.hubAddr = hubAddr;
        this.voice = new VoiceChannel(this::routeVoice);
    }

    // ---- the frame pump: the lobby screen calls this every frame --------------------------

    public void update() {
        update(System.currentTimeMillis());
    }

    /** The test clock: same pump, any moment you like. */
    void update(long now) {
        Transport.Received r;
        while ((r = net.poll()) != null) {
            try {
                route(r.data, r.from, now);
            } catch (RuntimeException e) {
                // one malformed packet must never kill the session
            }
        }
        if (now >= nextBeat) {
            nextBeat = now + HEARTBEAT;
            beat(now);
        }
        if (state == State.JOINING) {
            if (now >= nextKnock) {
                nextKnock = now + KNOCK_RETRY;
                sayHello(); // a host re-welcomes a repeat knock from the same address
            }
            if (joinedAt < 0) joinedAt = now;
            if (now - joinedAt > 20000) { // ten unanswered HELLOs
                state = State.LOST;
                status = "NO ROOM FOUND — CHECK THE CODE AND THE HOST";
            }
        }
        if (state == State.LOST) voice.dispose(); // the microphone must not stay open
    }

    /** Is this side actually in contact? Everything except JOINING (knocking) and LOST
     *  (gone) counts — a RUN in progress keeps talking. */
    private boolean connected() {
        return state != State.JOINING && state != State.LOST;
    }

    /** Is this side actively sitting in the lobby (ready flips and starts mean something)? */
    private boolean inLobby() {
        return isHost() ? connected() : state == State.LIVE;
    }

    private void beat(long now) {
        if (isHost()) {
            sweep(now);
            broadcastRoster(); // the roster is the host's heartbeat too
        } else if (state == State.JOINING) {
            sayHello();
        } else if (state == State.LIVE || state == State.HANDOFF || state == State.RUN) {
            ping();
            if (now - lastSeen.getOrDefault(myId, now) > TIMEOUT) {
                state = State.LOST;
                status = "HOST LOST";
            }
        }
    }

    // ---- packet dispatch -------------------------------------------------------------------

    // ---- the game planes: [tag 2..6][senderId][binary body] ---------------------------------

    /** A listener that wants the run's game planes as raw binary. Screens attach on entry
     *  and detach on dispose; only the live seat of a race's run screen listens. */
    public interface NetSink {
        void plane(int tag, int sender, byte[] body, int off, int len);
    }

    private NetSink sink;

    /** Attach (or clear with null) the game-plane listener. */
    public void listen(NetSink s) {
        sink = s;
    }

    /** A game plane out to everyone else. The builder stamps [tag][sender] itself, so
     *  this forwards the bytes as they stand — one wrap, read back symmetrically. */
    public void sendPlane(int tag, byte[] body) {
        if (!connected() || body.length < 2) return;
        broadcast(body);
    }

    private void route(byte[] packet, SocketAddress from, long now) {
        if (packet.length < 2) return;
        int t = packet[0] & 0xFF;
        if (isHost() && t >= 1 && t <= 7) { // any traffic from a seated pilot proves they are there
            int who = packet[1] & 0xFF;
            SocketAddress seat = addrOf.get(who);
            if (seat != null && seat.equals(from)) lastSeen.put(who, now);
        }
        if (t == 1) { // voice: [1][senderId][payload]
            if (packet.length > 2 && connected()) {
                if (isHost()) relayVoice(packet, from);
                int sender = packet[1] & 0xFF;
                if (sender != myId) voice.receive(sender, trim(packet, 2)); // never hear yourself
            }
            return;
        }
        if (t >= 2 && t <= 7) { // the game planes: the hub passes them on, every seat sees every seat
            if (connected() && packet.length > 2) {
                if (isHost()) broadcast(packet); // the hub's own planes reach everyone
                if (sink != null) sink.plane(t, packet[1] & 0xFF, packet, 2, packet.length - 2);
            }
            return;
        }
        if (t > 7) return; // not one of ours (the LAN beacon lands here on the host's own socket)
        Msg m = Msg.unpack(packet);
        if (m == null || m.t == null) return; // a corrupt packet is dropped, never fatal
        switch (m.t) {
            case Msg.HELLO:   if (isHost()) onHello(m, from, now); break;
            case Msg.WELCOME: if (state == State.JOINING) onWelcome(m, now); break;
            case Msg.ROSTER:  if (!isHost() && (state == State.LIVE || state == State.HANDOFF
                                  || state == State.RUN)) {
                                  rosterStamp = m.num; // my next ping echoes this stamp home
                                  mirror(m.members);
                                  lastSeen.put(myId, now);
                              }
                              break;
            case Msg.READY:   if (isHost()) onReady(m); break;
            case Msg.START:   if (!isHost() && state == State.LIVE) onStart(m); break;
            case Msg.RESULT:  if (isHost()) onResult(m); break;
            case Msg.PING:    if (isHost()) {
                                  lastSeen.put(m.id, now);
                                  measure(m); // the client's echo of my roster stamp = my RTT to them
                                  SocketAddress back = addrOf.get(m.id);
                                  if (back != null) net.send(Msg.pack(m), back); // echo it unchanged
                              } else measureEcho(m); // the echo of my own stamp = my RTT to the hub
                              break;
            case Msg.BYE:     if (isHost()) drop(m.id, "PILOT LEFT"); break;
        }
    }

    // ---- the host's side -------------------------------------------------------------------

    private void onHello(Msg m, SocketAddress from, long now) {
        if (state != State.OPEN && state != State.LIVE) return;
        // a wire address that already knocks is a straggler's retry, not a new pilot:
        // re-welcome them instead of minting their seat twice (ids were drifting phantom)
        for (Msg.Member member : members) {
            SocketAddress known = addrOf.get(member.id);
            if (member.id != 0 && known != null && known.equals(from)) {
                Msg welcome = new Msg();
                welcome.t = Msg.WELCOME;
                welcome.id = member.id;
                welcome.members = roster();
                net.send(Msg.pack(welcome), from);
                return;
            }
        }
        if (members.size() >= MAX) return;
        int seat = freeSeat();
        if (seat < 0) return; // no seat left below the cap: no welcome, no half-made pilot
        Msg.Member member = new Msg.Member(seat,
                vajra.profile.Profiles.sanitize(m.name, "PILOT"), false);
        members.add(member);
        lastSeen.put(member.id, now);
        addrOf.put(member.id, from);
        Msg welcome = new Msg();
        welcome.t = Msg.WELCOME;
        welcome.id = member.id;
        welcome.members = roster();
        net.send(Msg.pack(welcome), from);
        broadcastRoster();
        state = State.LIVE; // the room is open and someone is in it
        status = "PILOT " + member.name + " JOINED";
    }

    /** The lowest roster id above the host's that nobody is wearing. Ids are RECYCLED, not
     *  minted forever: SeatColors keys the hue off the id, so a seat past 3 would repaint a
     *  colour already on the roster (a fresh guest drawn in the host's pink). The host holds
     *  0 and onHello admits only under MAX, so a free seat always exists here. */
    private int freeSeat() {
        for (int id = 1; id < MAX; id++) {
            boolean taken = false;
            for (Msg.Member m : members) if (m.id == id) { taken = true; break; }
            if (!taken) return id;
        }
        return -1;
    }

    private void onReady(Msg m) {
        for (Msg.Member member : members) {
            if (member.id == m.id) {
                member.ready = m.flag;
                broadcastRoster();
                return;
            }
        }
    }

    /** A flight's score lands in the roster's book — best flight wins the line, so a
     *  re-flown seed can only improve a pilot's number. The rebroadcast shows it to all. */
    private void onResult(Msg m) {
        for (Msg.Member member : members) {
            if (member.id == m.id) {
                member.points = Math.max(member.points, m.num);
                broadcastRoster();
                return;
            }
        }
    }

    /** A pilot's own flight is over: the book keeps the best. The host writes locally; a
     *  client tells the hub and reads the truth back off the next roster. */
    public void report(long points) {
        if (state != State.RUN && state != State.HANDOFF) return;
        if (isHost()) {
            onResult(envelope(Msg.RESULT, myId(), points));
        } else {
            net.send(Msg.pack(envelope(Msg.RESULT, myId, points)), hubAddr);
        }
    }

    private static Msg envelope(String type, int id, long num) {
        Msg m = new Msg();
        m.t = type;
        m.id = id;
        m.num = num;
        return m;
    }

    /** The seed is locked: everyone moves to HANDOFF. A solo host starts alone when they
     *  ready up — the same lock holds: EVERY pilot on the roster must be ready. x3 because
     *  UDP loses one happily. */
    public void start() {
        if (!isHost() || !(state == State.OPEN || state == State.LIVE)) return;
        for (Msg.Member member : members) if (!member.ready) return;
        seed = System.nanoTime() & 0x3FFFFFFFFFFFFFFFL;
        for (Msg.Member member : members) { // a fresh race: a fresh ledger, everyone unready
            member.points = 0;
            member.ready = false;
        }
        for (int i = 0; i < 3; i++) broadcastStart();
        state = State.HANDOFF;
        status = "RUN " + seed + " LOCKED";
        broadcastRoster(); // everyone's ready flag resets with the seed's broadcast
    }

    private void broadcastStart() {
        Msg m = new Msg();
        m.t = Msg.START;
        m.num = seed;
        broadcast(Msg.pack(m));
    }

    /** Members silent for TIMEOUT go overboard; the sweep runs once per heartbeat. */
    private void sweep(long now) {
        for (int i = members.size() - 1; i >= 0; i--) {
            Msg.Member member = members.get(i);
            if (member.id == myId) continue;
            if (now - lastSeen.getOrDefault(member.id, now) > TIMEOUT) drop(member.id, "PILOT DROPPED");
        }
    }

    private void drop(int id, String why) {
        for (int i = members.size() - 1; i >= 0; i--) {
            if (members.get(i).id == id) {
                members.remove(i);
                addrOf.remove(id);
                lastSeen.remove(id);
                pings.remove(id); // a recycled seat must not inherit the last pilot's RTT
                broadcastRoster();
                status = why;
                return;
            }
        }
    }

    // ---- the client's side -----------------------------------------------------------------

    private void sayHello() {
        Msg m = new Msg();
        m.t = Msg.HELLO;
        m.name = myName;
        net.send(Msg.pack(m), hubAddr);
    }

    private void onWelcome(Msg m, long now) {
        myId = m.id;
        voice.setMyId(m.id);
        mirror(m.members);
        lastSeen.put(myId, now);
        state = State.LIVE;
        status = "LOBBY";
    }

    private void onStart(Msg m) {
        if (seed != 0) return; // the x3 copies dedupe here: a seed can never be 0
        seed = m.num;
        state = State.HANDOFF;
        status = "RUN " + seed + " LOCKED";
    }

    // ---- ping law: the roster carries the host's clock stamp, a PING echoes a query --------

    /** The client asks; the host answers and measures. A query stamps the client's clock
     *  and echoes the freshest roster stamp back; an echo of MY stamp is my RTT. The two
     *  clocks never meet — every measure subtracts within one machine's own clock. */
    private void ping() {
        Msg m = new Msg();
        m.t = Msg.PING;
        m.id = myId;
        m.num = System.currentTimeMillis();
        myStamps[stampAt = (stampAt + 1) & 3] = m.num;
        m.stamp = rosterStamp; // the host reads its own stamp back with the pilot's echo
        net.send(Msg.pack(m), hubAddr);
    }

    /** Host side: the pilot's echo carries my stamp, so the gap is the wire's round trip. */
    private void measure(Msg m) {
        long rtt = System.currentTimeMillis() - m.stamp;
        if (m.stamp == rosterStamp && rtt >= 0 && rtt < HEARTBEAT * 4)
            smooth(m.id, rtt); // only the freshest roster's stamp measures; stragglers pass
    }

    /** Client side: the stamp is one of mine, so the gap is my RTT to the hub. */
    private void measureEcho(Msg m) {
        long rtt = System.currentTimeMillis() - m.num;
        for (long s : myStamps)
            if (s == m.num && rtt >= 0 && rtt < HEARTBEAT * 4) { smooth(myId, rtt); return; }
    }

    /** EWMA holds the read steady while a packet jitters single flights. A 0-gap (finer
     *  than the clock's own resolution) still counts as a real measure. */
    private void smooth(int id, long rtt) {
        pings.merge(id, (float) Math.max(1, rtt), (old, add) -> old <= 0 ? add : old * 0.7f + add * 0.3f);
        epoch++;
    }

    /** The smoothed round-trip to that pilot, ms. 0 = not measured yet. */
    public int pingOf(int id) {
        Float p = pings.get(id);
        return p == null || p <= 0 ? 0 : Math.round(p);
    }

    /** The roster id the hub always wears: the host is member 0 in every roster. */
    public static final int HOST_ID = 0;

    /** The round trip to that pilot, as THIS machine knows it. The host measures every guest
     *  directly; a guest measures the hub itself and reads everyone else off the roster (the
     *  hub's own measure of them). 0 = not measured yet, and 0 for your own row — a delay to
     *  yourself has no meaning. */
    public int pingTo(int id) {
        if (id == myId()) return 0;
        if (!isHost() && id == HOST_ID) return pingOf(myId()); // the hub, as this seat sees it
        return pingOf(id);
    }

    /** Whether that id sits on the roster — the run's mirror prunes dead seats with it. */
    public boolean seatAlive(int id) {
        for (Msg.Member m : members) if (m.id == id) return true;
        return false;
    }

    /** Bumps whenever a roster churn or a fresh measure lands: the HUD's crew list caches on it. */
    public long epoch() { return epoch; }

    // ---- voice routing: client -> hub -> everyone else -------------------------------------


    /** VoiceChannel hands over [senderId][payload] from the local mic; the session tags
     *  and aims it. The host relays to every OTHER pilot; a client sends to the hub. */
    private void routeVoice(byte[] frame) {
        if (!connected()) return;
        byte[] packet = new byte[frame.length + 1];
        packet[0] = 1; // voice tag
        System.arraycopy(frame, 0, packet, 1, frame.length);
        if (isHost()) {
            int sender = frame[0] & 0xFF;
            for (Msg.Member member : members) {
                if (member.id == sender || member.id == myId) continue;
                SocketAddress to = addrOf.get(member.id);
                if (to != null) net.send(packet, to);
            }
        } else {
            net.send(packet, hubAddr);
        }
    }

    private void relayVoice(byte[] packet, SocketAddress from) {
        int sender = packet[1] & 0xFF;
        for (Msg.Member member : members) {
            if (member.id == sender || member.id == myId) continue;
            SocketAddress to = addrOf.get(member.id);
            if (to != null && !to.equals(from)) net.send(packet, to);
        }
    }

    // ---- roster plumbing -------------------------------------------------------------------

    private ArrayList<Msg.Member> roster() {
        ArrayList<Msg.Member> copy = new ArrayList<>(members.size());
        for (Msg.Member member : members) {
            Msg.Member out = new Msg.Member(member.id, member.name, member.ready);
            out.points = member.points; // the race's book rides the roster to every mirror
            out.ping = pingOf(member.id); // the host's measure rides the roster (0 = unknown)
            copy.add(out);
        }
        return copy;
    }

    private void mirror(ArrayList<Msg.Member> truth) {
        members.clear();
        if (truth != null) members.addAll(truth);
        if (truth != null) // the host's measures ride in; my own row keeps my local read fresher
            for (Msg.Member member : truth) if (member.ping > 0) pings.put(member.id, (float) member.ping);
        epoch++;
    }

    private void broadcastRoster() {
        Msg m = new Msg();
        m.t = Msg.ROSTER;
        m.members = roster();
        m.num = rosterStamp = System.currentTimeMillis(); // the RTT ruler the pings echo back
        broadcast(Msg.pack(m));
    }

    private void broadcast(byte[] packet) {
        if (!isHost()) { net.send(packet, hubAddr); return; } // the hub forwards; one copy, not per pilot
        for (Msg.Member member : members) {
            if (member.id == myId) continue;
            SocketAddress to = addrOf.get(member.id);
            if (to != null) net.send(packet, to);
        }
    }

    // ---- what the screens read -------------------------------------------------------------

    public State state() { return state; }
    public String status() { return status; }
    /** The wire under this session — the lobby asks it for a relay room code. */
    public Transport transport() { return net; }
    public boolean isHost() { return hubAddr == null; }
    public int myId() { return myId < 0 ? 0 : myId; }
    public int count() { return members.size(); }
    public Msg.Member member(int i) { return members.get(i); }

    /** The pilot whose seat id rings here — the name over a hull asks it. */
    public String nameOf(int id) {
        for (Msg.Member m : members) if (m.id == id) return m.name;
        return "PILOT";
    }
    public long seed() { return seed; }

    /** My ready flag, or false while JOINING. */
    public boolean ready() {
        for (Msg.Member member : members) if (member.id == myId()) return member.ready;
        return false;
    }

    /** Flip my flag: a client flips locally then tells the hub, whose roster rebroadcast
     *  is the reconciliation. The host's own flip is local truth + a rebroadcast. */
    public void toggleReady() {
        if (!inLobby()) return;
        for (Msg.Member member : members) {
            if (member.id == myId()) {
                member.ready = !member.ready;
                if (isHost()) {
                    broadcastRoster();
                } else {
                    Msg m = new Msg();
                    m.t = Msg.READY;
                    m.id = myId;
                    m.flag = member.ready;
                    net.send(Msg.pack(m), hubAddr);
                }
                return;
            }
        }
    }

    /** A pilot walked out of a race back into the lobby: their own state returns to LIVE.
     *  The pilots still flying keep RUN; the ready gate keeps the host from restarting
     *  over their heads until they, too, come back and ready up. The seed resets here —
     *  the START dedupe keys on it, and never resetting meant no second race could ever
     *  lock. */
    public void backToLobby() {
        if (state == State.RUN) {
            state = State.LIVE;
            seed = 0;
            status = "LOBBY";
        }
    }

    /** The run screen takes over: the handoff is consumed and the race is ON. */
    public void enterRun() {
        if (state == State.HANDOFF) {
            state = State.RUN;
            status = "RACE ON";
        }
    }

    /** Leave cleanly: BYE (x2, UDP), then the transport and the voice lines close. */
    public void leave() {
        if (!isHost()) {
            Msg m = new Msg();
            m.t = Msg.BYE;
            m.id = myId; // the id rides: a nameless BYE once made the host drop itself
            net.send(Msg.pack(m), hubAddr);
            net.send(Msg.pack(m), hubAddr);
        }
        close();
        status = "LEFT";
    }

    public void close() {
        state = State.LOST;
        net.close();
        voice.dispose();
    }

    private static byte[] trim(byte[] packet, int at) {
        byte[] out = new byte[packet.length - at];
        System.arraycopy(packet, at, out, 0, out.length);
        return out;
    }
}
