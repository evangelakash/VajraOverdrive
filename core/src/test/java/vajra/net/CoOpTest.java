package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import vajra.enemy.Enemy;
import vajra.ship.ShipControls;
import vajra.world.Pickup;
import vajra.world.Score;
import vajra.world.World;
import vajra.weapon.Bullet;

/** Phase 3's gate (docs/MULTIPLAYER.md): two loopback sessions with 60 ms artificial
 *  latency per hop, both worlds from the same seed, driven by one ShipControls script per
 *  seat. The host's copy of the guest and the guest's copy of the host must stay within
 *  half a tile (16 px) while both boosts; then one round of each game plane is walked:
 *  a guest shot relays and lands on the host's ledger, a hostile touch on the guest's
 *  mirror relays home and bites the guest's hull, a partner kill pays the partner's book,
 *  a sweep on the guest retires the host's drop, and the host's run-over bit ends the
 *  guest's run with it. No sockets, no sleeps — a fake clock and a late queue. */
class CoOpTest {
    private static final long T0 = 1_000;
    private static final InetSocketAddress ANY = new InetSocketAddress(26000);
    private static final float STEP = 1 / 60f;
    private static final long STEP_MS = 1000 / 60; // the fake clock ticks one step at a time
    private static final float LAT = 60;          // artificial one-way latency, ms

    /** A transport that delivers each send one hop later, when the shared clock has
     *  reached its due time. Both ends share one long[] cell the test steps forward. */
    private static final class Late implements Transport {
        private static final class Pkt {
            final byte[] data;
            final SocketAddress from;
            final long due;

            Pkt(byte[] data, SocketAddress from, long due) {
                this.data = data;
                this.from = from;
                this.due = due;
            }
        }

        private final ArrayDeque<Pkt> in = new ArrayDeque<>();
        private Late peer;
        private final long[] clock;

        Late(long[] clock) {
            this.clock = clock;
        }

        static void pair(Late a, Late b) {
            a.peer = b;
            b.peer = a;
        }

        @Override
        public void send(byte[] data, SocketAddress to) {
            peer.in.add(new Pkt(data, to, clock[0] + (long) LAT));
        }

        @Override
        public Transport.Received poll() {
            Pkt p = in.peekFirst();
            if (p == null || p.due > clock[0]) return null;
            in.pollFirst();
            return new Transport.Received(p.data, p.from);
        }

        @Override
        public void close() {
            peer = null;
        }
    }

    /** Both sessions and both worlds, wired through 60 ms of fake wire. */
    private static final class Race {
        final long[] clock = {T0};
        final Late srcA = new Late(clock), srcB = new Late(clock);
        final Session hostS;
        final Session clientS;
        final World hostW;
        final World clientW;

        Race() {
            Late.pair(srcA, srcB);
            hostS = Session.host(srcA, "AGNI");
            clientS = Session.join(srcB, "VARUNA", ANY); // HELLO leaves at T0, due +60
            clock[0] = T0 + 100;
            hostS.update(clock[0]);      // host hears the hello; WELCOME due 160
            clock[0] = T0 + 300;
            clientS.update(clock[0]);    // WELCOME lands → LIVE
            hostS.update(clock[0]);
            assertEquals(Session.State.LIVE, clientS.state());

            clientS.toggleReady();       // READY due 360
            hostS.toggleReady();
            clock[0] = T0 + 500;
            clientS.update(clock[0]);
            hostS.update(clock[0]);      // host sees READY
            hostS.start();               // everyone ready → HANDOFF
            hostS.enterRun();
            clock[0] = T0 + 700;
            clientS.update(clock[0]);    // START lands despite the delay
            clientS.enterRun();

            assertEquals(Session.State.RUN, hostS.state());
            assertEquals(Session.State.RUN, clientS.state());

            hostW = new World(hostS.seed());
            clientW = new World(clientS.seed());
            assertEquals(hostS.seed(), clientS.seed(), "both seats fly one seed");
            clientW.clientSeat = true;

            hostS.listen((t, s, bo, o, l) -> { planes[t]++; GameWire.apply(hostS, hostW, t, s, bo, o, l); });
            clientS.listen((t, s, bo, o, l) -> { planes[t]++; GameWire.apply(clientS, clientW, t, s, bo, o, l); });
        }

        final int[] planes = new int[8];

        String seenPlanes() {
            StringBuilder b = new StringBuilder();
            for (int i = 2; i < 7; i++)
                if (planes[i] > 0) b.append(i).append(':').append(planes[i]).append(' ');
            return b.toString();
        }

        /** One sim+wire beat: both worlds step, both seats pump, both sessions route. */
        void beat(ShipControls h, ShipControls c) {
            hostW.update(STEP, h);
            clientW.update(STEP, c);
            GameWire.pump(hostS, hostW, STEP);
            GameWire.pump(clientS, clientW, STEP);
            clock[0] += STEP_MS;
            hostS.update(clock[0]);
            clientS.update(clock[0]);
        }

        void settle(int steps, ShipControls h, ShipControls c) {
            for (int i = 0; i < steps; i++)
                beat(h, c);
        }
    }

    private static ShipControls idle = new ShipControls() {
        public float thrustY() { return 0; }
        public float thrustX() { return 0; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return false; }
        public boolean dash() { return false; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    };

    private static ShipControls boost = new ShipControls() {
        public float thrustY() { return 1; }
        public float thrustX() { return 0; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return true; }
        public boolean dash() { return false; }
        public boolean shield() { return false; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    };

    @Test
    void bothMirrorsStayInsideHalfATileUnderLatency() {
        Race r = new Race();
        r.settle(240, boost, boost); // 4 s of straight full-boost flight on both seats

        float dx = r.hostW.partner.x - r.clientW.ship.x;
        float dy = r.hostW.partner.y - r.clientW.ship.y;
        assertTrue(dx * dx + dy * dy < 16 * 16, "the host sees the guest within half a tile");
        dx = r.clientW.partner.x - r.hostW.ship.x;
        dy = r.clientW.partner.y - r.hostW.ship.y;
        assertTrue(dx * dx + dy * dy < 16 * 16, "the guest sees the host within half a tile");
    }

    @Test
    void aGuestRoundRelaysAndLandsInTheHostsWorld() {
        Race r = new Race();
        r.settle(60, idle, idle); // a few beats settle both mirrors, cadence warm

        // a plain round out of the guest's gun: the same fields the chokes would stamp
        r.clientW.spawnBullet(r.clientW.ship.x, r.clientW.ship.y, 0f, Bullet.HOSTILE, 1f, 1f);
        r.settle(60, idle, idle); // the event rides up the wire and lands

        Bullet relayed = null;
        for (Bullet b : r.hostW.bullets)
            if (!b.hostile && b.owner == 1) relayed = b;
        assertNotNull(relayed, "the guest's shot exists in the host's sky, owned");
        assertEquals(Bullet.HOSTILE.damage, relayed.damage, "the round's bite travels");
    }

    @Test
    void aTouchOnThePartnerHullBitesThePartnerHull() {
        Race r = new Race();
        r.settle(60, idle, boost); // the guest pulls away; hulls share a spawn, so split them
        r.settle(15, idle, idle);
        r.clientW.ship.hp = 100; // floor the noise: any pain after this shows
        r.clientW.ship.iFrames = 0;
        float before = r.clientW.ship.hp;

        // a hostile round straight onto the guest's mirror — the hub owns the fight
        r.hostW.spawnEnemyBullet(r.hostW.partner.x, r.hostW.partner.y, 0f);
        r.settle(45, idle, idle);

        assertTrue(r.clientW.ship.hp < before, "the owner's hull heard the touch through pain "
                + "(partnerHp=" + (int) r.hostW.partner.hp + " hpAfter=" + (int) r.clientW.ship.hp
                + " planes=" + r.seenPlanes() + ")");
    }

    @Test
    void aPartnerKillPaysThePartnerBook() {
        Race r = new Race();
        r.settle(30, idle, idle);

        // a kill the hub resolves with a guest's round in the barrel — the relay stamps
        // the owner, so the countKill attribution says pilot 1 and the pay flies home
        r.hostW.shooter = 1;
        r.hostW.countKill(new Enemy(Enemy.Kind.VAKRA, r.hostW.ship.x + 50, r.hostW.ship.y));
        r.settle(30, idle, idle);

        assertEquals(1, r.clientW.score.combo, "their chain opened");
        assertEquals(Score.TIER[0] + Enemy.Kind.VAKRA.hp, r.clientW.score.points, "the flat landed on their book");
    }

    @Test
    void aGuestSweepRetiresTheHostsDrop() {
        Race r = new Race();
        r.settle(60, idle, idle); // cadence warm

        Pickup p = new Pickup();
        p.netId = 99; // as if the host's ledger stamped it
        p.type = Pickup.COIN;
        p.x = 1000;
        p.y = 1000;
        p.vx = 0;
        p.vy = 0;
        r.hostW.pickups.add(0, p); // the head: the swarm's row cap takes the list's front
        r.settle(60, idle, idle); // the swarm carries it to the guest

        Pickup ghost = null;
        for (Pickup g : r.clientW.pickups)
            if (g.netId == 99) ghost = g;
        assertNotNull(ghost, "the drop arrived as a ground ghost (clientPickups=" + r.clientW.pickups.size()
                + " hostPickups=" + r.hostW.pickups.size() + " planes=" + r.seenPlanes() + ")");

        r.clientW.ship.x = ghost.x; // sweep it on the guest's seat
        r.clientW.ship.y = ghost.y;
        r.settle(60, idle, idle);

        assertTrue(r.clientW.pickups.isEmpty(), "the guest's copy went with the sweep");
        for (Pickup g : r.hostW.pickups)
            assertNotEquals(99, g.netId, "the TAKE retired the host's drop");
    }

    @Test
    void theHostsRunOverEndsTheGuestsRun() {
        Race r = new Race();
        r.settle(30, idle, idle);
        assertFalse(r.clientW.runEnded());

        r.hostW.gameOver = true; // the hub's own hull spent its lives
        r.settle(60, idle, idle); // one swarm is enough to carry the bit

        assertTrue(r.clientW.runEnded(), "the guest's run ends with the host's");
    }
}
