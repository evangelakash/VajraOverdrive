package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vajra.enemy.Enemy;
import vajra.world.Pickup;
import vajra.world.World;

/** The co-op streaming gate: the snapshot must fit one datagram, a guest must never resolve
 *  the fight itself, the hub must carry every seat's planes to every other seat, and a guest
 *  must be able to read the hub's round trip. */
class CoOpStreamTest {

    // ---- a three-seat wire, routed by address -------------------------------------------------

    /** One seat's endpoint. Every send is routed by address, so the hub's relay reaches
     *  every other seat — which is exactly what the two-seat loopback cannot prove. */
    private static final class Bus implements Transport {
        static final Map<SocketAddress, Bus> REGISTRY = new LinkedHashMap<>();
        final ArrayDeque<Received> in = new ArrayDeque<>();
        final List<int[]> sizes = new ArrayList<>(); // the length of every frame put on the wire
        final SocketAddress addr;

        Bus(int port) {
            addr = new InetSocketAddress("127.0.0.1", port);
            REGISTRY.put(addr, this);
        }

        Bus join() {
            return new Bus(30000 + REGISTRY.size());
        }

        @Override
        public void send(byte[] data, SocketAddress to) {
            sizes.add(new int[] { data.length });
            Bus dst = REGISTRY.get(to);
            if (dst != null) dst.in.add(new Received(data, addr));
        }

        @Override
        public Received poll() {
            return in.pollFirst();
        }

        @Override
        public void close() {
            REGISTRY.remove(addr);
        }
    }

    /** Every room starts on an empty wire: a leftover endpoint from another test steals its ports. */
    private static Bus fresh() {
        Bus.REGISTRY.clear();
        return new Bus(30000);
    }

    /** A host and two guests, all LIVE, sharing one bus. */
    private static final class Room {
        final Bus host = fresh();
        final Bus g1 = host.join(), g2 = host.join();
        final Session hs, s1, s2;
        final World hw, w1, w2;

        Room() {
            hs = Session.host(host, "AGNI");
            s1 = Session.join(g1, "VARUNA", host.addr);
            s2 = Session.join(g2, "KALA", host.addr);
            for (int i = 0; i < 12; i++) { hs.update(); s1.update(); s2.update(); }
            s1.toggleReady();
            s2.toggleReady();
            hs.toggleReady();
            for (int i = 0; i < 12; i++) { hs.update(); s1.update(); s2.update(); }
            hs.start();
            hs.enterRun();
            s1.enterRun();
            s2.enterRun();
            for (int i = 0; i < 12; i++) { hs.update(); s1.update(); s2.update(); }
            hw = new World(hs.seed());
            w1 = new World(s1.seed());
            w2 = new World(s2.seed());
            w1.seatAsClient();
            w2.seatAsClient();
            hs.listen((t, s, bo, o, l) -> GameWire.apply(hs, hw, t, s, bo, o, l));
            s1.listen((t, s, bo, o, l) -> GameWire.apply(s1, w1, t, s, bo, o, l));
            s2.listen((t, s, bo, o, l) -> GameWire.apply(s2, w2, t, s, bo, o, l));
        }

        void beat() {
            hw.update(1 / 60f, vajra.ship.ShipControls.NONE);
            w1.update(1 / 60f, vajra.ship.ShipControls.NONE);
            w2.update(1 / 60f, vajra.ship.ShipControls.NONE);
            GameWire.pump(hs, hw, 1 / 60f);
            GameWire.pump(s1, w1, 1 / 60f);
            GameWire.pump(s2, w2, 1 / 60f);
            hs.update();
            s1.update();
            s2.update();
        }
    }

    // ---- the tests ----------------------------------------------------------------------------

    /** THE OVERFLOW: the snapshot's buffer was one byte short of what it writes, so a busy
     *  host (a hundred foes and two dozen drops) threw inside the send and killed the run. */
    @Test
    void theSwarmSnapshotFitsOneDatagram() {
        Room r = new Room();
        for (int i = 0; i < 24; i++) { // the ground litter of a busy fight
            Pickup p = new Pickup();
            p.netId = 900 + i;
            p.type = Pickup.COIN;
            p.x = 2600;
            p.y = 2000;
            r.hw.pickups.add(p);
        }
        r.beat();
        r.beat(); // the swarm goes out at 10 Hz: give it a beat to land
        int biggest = 0;
        for (int[] n : r.host.sizes) biggest = Math.max(biggest, n[0]);
        assertTrue(biggest < 1200, "the snapshot stays inside one datagram: " + biggest);
        assertTrue(biggest > 0, "the host actually sent something");
    }

    /** A guest must never resolve the fight: its copy of a foe is the host's word about a
     *  foe, and hurting it used to score a phantom kill, drop phantom loot and delete the
     *  host's real drops through the TAKE plane. */
    @Test
    void aGuestCannotHurtTheHostsFoes() {
        Room r = new Room();
        r.beat();
        r.beat();
        assertFalse(r.w1.enemies.isEmpty(), "the guest received the swarm");
        Enemy ghost = r.w1.enemies.get(0);
        assertTrue(ghost.ghost, "the guest's foes are ghosts");
        float hp = ghost.hp;
        assertFalse(ghost.hurt(999f, r.w1), "a ghost cannot be killed on a guest seat");
        assertEquals(hp, ghost.hp, 1e-4, "and it takes no damage either");
        assertEquals(0, r.w1.score.points, "no phantom kill, no phantom score");
        assertTrue(r.w1.pickups.isEmpty(), "and no phantom loot to take the host's drops with");
    }

    /** The hub carries every seat's planes to every other seat: three pilots in a room each
     *  see the other two, not only the host. */
    @Test
    void threeSeatsSeeEachOther() {
        Room r = new Room();
        r.w1.ship.x = 2600;
        r.w1.ship.y = 900;
        r.w2.ship.x = 2600;
        r.w2.ship.y = 900;
        for (int i = 0; i < 60; i++) r.beat(); // the SHIP planes fly at 30 Hz
        assertNotNull(r.w1.partner, "the guest sees the hub");
        assertNotNull(r.w2.partner, "and so does the other guest");
        assertNotNull(r.w1.extra(2), "the first guest sees the second guest's hull");
        assertNotNull(r.w2.extra(1), "and the second sees the first");
    }

    /** A guest must be able to read the hub's round trip — the crew block's ping column was
     *  a row of dashes on every guest's screen. */
    @Test
    void aGuestReadsTheHubsRoundTrip() throws Exception {
        Room r = new Room();
        Thread.sleep(2200);
        r.beat();
        Thread.sleep(2200);
        r.beat();
        assertTrue(r.s1.pingTo(Session.HOST_ID) > 0,
                "the guest measures the hub it is talking to: " + r.s1.pingTo(Session.HOST_ID));
        assertEquals(0, r.s1.pingTo(r.s1.myId()), "and never a delay to itself");
        assertTrue(r.hs.pingTo(1) > 0, "the hub measures its guest: " + r.hs.pingTo(1));
    }

    /** THE JOINER'S LEAK: a guest's world never aged its effects, so smoke, exhaust and rings
     *  stayed on screen for ever and the list grew every frame. */
    @Test
    void aGuestRetiresItsEffects() {
        Room r = new Room();
        for (int i = 0; i < 40; i++) r.w1.addEffect(vajra.world.Effect.smoke(0, 0, 0, 0, 1, 1, 1));
        assertEquals(40, r.w1.effects.size());
        for (int i = 0; i < 60 * 8; i++) r.w1.update(1 / 60f, vajra.ship.ShipControls.NONE);
        assertTrue(r.w1.effects.size() < 5, "old smoke is gone after eight seconds: " + r.w1.effects.size());
    }

    /** The other pilot's nose: the heading rides the SHIP plane and eases in on the mirror. */
    @Test
    void thePartnersHeadingCrossesTheWire() {
        Room r = new Room();
        r.hw.ship.facing = 0f; // the host points east
        for (int i = 0; i < 30; i++) r.beat();
        assertNotNull(r.w1.partner, "the guest sees the host's hull");
        assertEquals(0f, vajra.world.World.wrapAngle(r.w1.partner.facing), 0.15f, "and which way it points");
    }

    /** The ping each pilot reads is the round trip, not a stale roster age. */
    @Test
    void everyoneReadsEveryonesPing() {
        Room r = new Room();
        for (int i = 0; i < 40; i++) r.beat();
        r.hs.update(System.currentTimeMillis() + 3000);
        assertTrue(r.s1.pingOf(r.s1.myId()) < 500, "a loopback ping is tiny: " + r.s1.pingOf(r.s1.myId()));
    }

    /** A pilot who leaves is noticed on the hub and on the other guest. */
    @Test
    void aDepartureIsAnnounced() {
        Room r = new Room();
        int before = r.s2.leftCount();
        r.s1.leave();
        for (int i = 0; i < 40; i++) r.beat();
        assertTrue(r.hs.leftCount() > 0, "the hub saw them go");
        assertEquals("VARUNA", r.hs.lastLeft());
        assertTrue(r.s2.leftCount() > before, "so did the other guest");
    }
}
