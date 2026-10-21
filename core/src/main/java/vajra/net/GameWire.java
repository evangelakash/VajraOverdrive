package vajra.net;

import vajra.enemy.Enemy;
import vajra.ship.Ship;
import vajra.ship.ShipDef;
import vajra.world.Pickup;
import vajra.world.World;
import vajra.weapon.Bullet;

/** The live-co-op wire: six binary planes on top of the two Session already speaks.
 *  Shape law: every plane is [tag][senderId][body...] and every body is a fixed-width
 *  struct of little-endian numbers ({@link Bin}) — no serialization, no ACKs. The
 *  roster's ids key all of it; position packets are absolute, so loss costs one frame
 *  of pose, never state.
 *
 *  <p>TRUTH TABLE (one sentence per hull): a pilot's hull is authoritative on the
 *  machine that flies it — pose, armour, lives, response drama. Everything else on a
 *  machine (the partner hull, the foes, the loot) is a ghost kept by snapshots from the
 *  seat that owns it. The host does not re-simulate the guest: bullet touches on the
 *  partner hull relay to the owner and the owner's own {@code Ship.damage} answers.
 *  Kills the partner lands ride back as PAIN_KILL credits; the book stays per-pilot. */
public final class GameWire {
    private GameWire() {}

    public static final int SHIP = 2;    // both ways, 30 Hz: one hull's pose
    public static final int SHOT = 3;    // both ways, per shot: one round, def-less ghost
    public static final int SWARM = 4;   // host -> guests, 20 Hz: foes + ground loot + run bits
    public static final int PAIN = 5;    // hub -> owner: a bullet touch, a shove, a kill credit
    public static final int TAKE = 6;    // guest -> hub: I swept loot id N on my machine
    public static final int ASTRA = 7;   // guest -> hub: my release named these foes; arm them

    public static final int PAIN_BULLET = 0, PAIN_SHOVE = 1, PAIN_KILL = 2;

    // body widths after [tag][senderId]; edges below are the pack/unpack pairs' contract
    static final int SHIP_FIELDS = 27; // def u8, hp u8, x y vx vy sentClock f32, flags u8, facing f32
    static final int SHOT_FIELDS = 28; // hostile u8, x y vx vy dmg radius f32, rgb u8
    static final int FOE_FIELDS = 12;  // id u16, x y f32, flavour u8, hp u8
    static final int LOOT_FIELDS = 13; // id u16, type u8, variant u8, x y f32
    /** Rows per snapshot. The caps are sized so a FULL snapshot stays under one Ethernet
     *  MTU (5 + 64*12 + 2 + 12*13 = 931 bytes) — a UDP datagram that fragments is a UDP
     *  datagram that loses a piece of the fight on a bad link. */
    static final int FOE_CAP = 64, LOOT_CAP = 12;
    /** [tag][sender][flags u8][foeCount u16][foe rows][lootCount u16][loot rows] */
    static final int SWARM_MAX = 5 + FOE_CAP * FOE_FIELDS + 2 + LOOT_CAP * LOOT_FIELDS;
    static final int PAIN_FIELDS = 18; // target u8, kind u8, a i64, b c f32
    static final int TAKE_FIELDS = 2;  // pickupId u16

    static final float SHIP_HZ = 1 / 30f, SWARM_HZ = 1 / 20f;

    /** One-frame send cadence, from each machine's own world. Screens (and the headless
     *  gate) call this every frame; the beat counters live on the world seat. */
    public static void pump(Session s, World w, float dt) {
        if (s == null || w == null || dt <= 0) return;
        if (s.state() != Session.State.RUN && s.state() != Session.State.HANDOFF) return;
        // dead seats' mirrors go cold: a leaver's hull must not drift or ram forever
        if (w.partner != null && !s.seatAlive(w.partnerSeat)) { w.partner = null; w.partnerSeat = 1; }
        for (int i = w.extras.size() - 1; i >= 0; i--)
            if (!s.seatAlive(w.extras.get(i).seat)) w.extras.remove(i);
        w.shotWire = shot -> sendShot(s, shot); // my rounds announce themselves at the chokes
        w.painWire = pain -> sendPain(s, pain); // bullet touches / shoves / kills call home
        if (!s.isHost()) // a guest's release is armed by the hub, where the fight lives
            w.astraWire = (ids, n) -> sendAstra(s, ids, n);

        w.shipBeat -= dt;
        if (w.shipBeat <= 0) {
            w.shipBeat = SHIP_HZ;
            sendShip(s, w);
        }
        if (s.isHost()) {
            w.swarmBeat -= dt;
            if (w.swarmBeat <= 0) {
                w.swarmBeat = SWARM_HZ;
                sendSwarm(s, w);
            }
        } else { // my sweeps ask the host to retire them; idempotent, so loss just re-asks
            for (int i = 0; i < w.takes.size(); i++) sendTake(s, w.takes.get(i));
            w.takes.clear();
        }
    }

    static void sendShip(Session s, World w) {
        byte[] out = new byte[2 + SHIP_FIELDS];
        out[0] = (byte) SHIP;
        out[1] = (byte) s.myId();
        out[2] = (byte) ShipDef.indexOf(w.ship.def);
        out[3] = (byte) hullFrac(w.ship);
        Bin.putFloat(out, 4, w.ship.x);
        Bin.putFloat(out, 8, w.ship.y);
        Bin.putFloat(out, 12, w.ship.vx);
        Bin.putFloat(out, 16, w.ship.vy);
        Bin.putFloat(out, 20, w.clock); // the pose's birth: the reader integrates the flight age
        out[24] = (byte) (w.ship.iFrames > 0 ? 1 : 0);
        Bin.putFloat(out, 25, w.ship.facing); // where the nose points: the other pilot reads it
        s.sendPlane(SHIP, out);
    }

    static int hullFrac(Ship h) {
        float f = h.hp / h.maxHp();
        return (int) Math.max(0, Math.min(255, f * 255));
    }

    static void sendShot(Session s, World.Shot shot) {
        byte[] out = new byte[2 + SHOT_FIELDS];
        out[0] = (byte) SHOT;
        out[1] = (byte) s.myId();
        out[2] = (byte) (shot.hostile ? 1 : 0);
        Bin.putFloat(out, 3, shot.x);
        Bin.putFloat(out, 7, shot.y);
        Bin.putFloat(out, 11, shot.vx);
        Bin.putFloat(out, 15, shot.vy);
        Bin.putFloat(out, 19, shot.damage);
        Bin.putFloat(out, 23, shot.radius);
        out[27] = (byte) (shot.r * 255);
        out[28] = (byte) (shot.g * 255);
        out[29] = (byte) (shot.b * 255);
        s.sendPlane(SHOT, out);
    }

    /** The host's world, one snapshot: the foes and the loot nearest a live seat, and the
     *  run's outcome bits.
     *  Layout: [flags u8][foeCount u16][foe rows: id u16 x y f32 flavour u8 hp u8] ×n
     *  [lootCount u16][loot rows: id u16 type u8 variant u8 x y f32] ×m.
     *
     *  <p>NEAREST, NOT FIRST: the snapshot used to walk the roster in placement order, so a
     *  guest saw the low bands and never the fight it was standing in — and once the roster
     *  outgrew the cap, the foes that fell out VANISHED on the guest while alive on the host.
     *  Now the rows are the nearest to any live seat (the host's hull and every mirror), which
     *  is the fight the pilots can actually see. */
    static void sendSwarm(Session s, World w) {
        byte[] out = new byte[SWARM_MAX];
        out[0] = (byte) SWARM;
        out[1] = (byte) s.myId();
        byte flags = 0;
        if (w.runEnded()) flags |= 1;
        if (w.won) flags |= 2;
        out[2] = flags;

        Enemy[] foes = nearestFoes(w, FOE_CAP);
        int n = foes.length;
        int o = 5; // the foe rows start after [tag][sender][flags][count u16]
        for (int i = 0; i < n; i++) {
            Enemy e = foes[i];
            out[o] = (byte) (e.netId & 0xFF);
            out[o + 1] = (byte) (e.netId >> 8);
            Bin.putFloat(out, o + 2, e.x);
            Bin.putFloat(out, o + 6, e.y);
            out[o + 10] = (byte) ((e.kind.ordinal() & 0x1F)
                    | (e.boss ? 0x20 : 0) | (e.elite ? 0x40 : 0) | (e.rank == 1 ? 0x80 : 0));
            out[o + 11] = (byte) (int) Math.max(0, Math.min(255, e.hp / e.maxHp * 255f));
            o += FOE_FIELDS;
        }
        out[3] = (byte) n;
        out[4] = (byte) (n >> 8);

        Pickup[] loot = nearestLoot(w, LOOT_CAP);
        int m = loot.length;
        int lootAt = o; // the count rides immediately after the foe rows
        o = lootAt + 2;
        for (int i = 0; i < m; i++) {
            Pickup p = loot[i];
            out[o] = (byte) (p.netId & 0xFF);
            out[o + 1] = (byte) (p.netId >> 8);
            out[o + 2] = (byte) p.type;
            out[o + 3] = (byte) p.variant;
            Bin.putFloat(out, o + 4, p.x);
            Bin.putFloat(out, o + 8, p.y);
            o += LOOT_FIELDS;
        }
        out[lootAt] = (byte) m;
        out[lootAt + 1] = (byte) (m >> 8);
        byte[] body = new byte[o];
        System.arraycopy(out, 0, body, 0, o);
        s.sendPlane(SWARM, body);
    }

    /** The want foes nearest any live seat. One pass of insertion into a caller-sized array:
     *  the roster is hundreds long and this runs ten times a second, so the simple O(n*k)
     *  selection is cheaper than any sort. */
    private static Enemy[] nearestFoes(World w, int want) {
        Enemy[] best = new Enemy[Math.min(want, w.enemies.size())];
        int filled = 0;
        for (Enemy e : w.enemies) {
            if (e.dead || e.ghost || e.bossHidden) continue;
            float d = seatDist2(w, e.x, e.y);
            int at = filled;
            while (at > 0 && seatDist2(w, best[at - 1].x, best[at - 1].y) > d) {
                if (at < best.length) best[at] = best[at - 1];
                at--;
            }
            if (at < best.length) best[at] = e;
            if (filled < best.length) filled++;
        }
        return best;
    }

    /** The want ground drops nearest any live seat — the loot a pilot can actually reach. */
    private static Pickup[] nearestLoot(World w, int want) {
        Pickup[] best = new Pickup[Math.min(want, w.pickups.size())];
        int filled = 0;
        for (Pickup p : w.pickups) {
            float d = seatDist2(w, p.x, p.y);
            int at = filled;
            while (at > 0 && seatDist2(w, best[at - 1].x, best[at - 1].y) > d) {
                if (at < best.length) best[at] = best[at - 1];
                at--;
            }
            if (at < best.length) best[at] = p;
            if (filled < best.length) filled++;
        }
        return best;
    }

    /** The squared distance from a point to the NEAREST live seat: the host's hull and every
     *  mirror, so a guest in another band still gets the foes around it. */
    private static float seatDist2(World w, float x, float y) {
        float best = d2(x, y, w.ship.x, w.ship.y);
        if (w.partner != null) best = Math.min(best, d2(x, y, w.partner.x, w.partner.y));
        for (World.SeatHull gh : w.extras)
            if (gh.ship != null) best = Math.min(best, d2(x, y, gh.ship.x, gh.ship.y));
        return best;
    }

    private static float d2(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return dx * dx + dy * dy;
    }

    static void sendPain(Session s, World.Pain pain) {
        byte[] out = new byte[2 + PAIN_FIELDS];
        out[0] = (byte) PAIN;
        out[1] = (byte) s.myId();
        out[2] = (byte) pain.target;
        out[3] = (byte) pain.kind;
        Bin.putLong(out, 4, pain.a);
        Bin.putFloat(out, 12, pain.b);
        Bin.putFloat(out, 16, pain.c);
        s.sendPlane(PAIN, out); // pains broadcast; each seat's sink drops what isn't for it
    }

    static void sendTake(Session s, int pickupId) {
        byte[] out = new byte[2 + TAKE_FIELDS];
        out[0] = (byte) TAKE;
        out[1] = (byte) s.myId();
        out[2] = (byte) pickupId;
        out[3] = (byte) (pickupId >> 8);
        s.sendPlane(TAKE, out);
    }

    /** A release on a guest seat: the foes it aimed at, by the host's own ids. The hub arms
     *  the same volley there — the Astra is a real weapon on every seat. */
    static void sendAstra(Session s, int[] targetIds, int count) {
        int n = Math.min(Math.min(count, targetIds.length), 255);
        byte[] out = new byte[2 + 1 + n * 2];
        out[0] = (byte) ASTRA;
        out[1] = (byte) s.myId();
        out[2] = (byte) n;
        for (int i = 0; i < n; i++) {
            out[3 + i * 2] = (byte) (targetIds[i] & 0xFF);
            out[4 + i * 2] = (byte) (targetIds[i] >> 8);
        }
        s.sendPlane(ASTRA, out);
    }

    // ---- inbound -------------------------------------------------------------------------------

    /** The single distribute patch: every received game plane lands here. Seat gates: the
     *  senior hull (the host's on a guest, pilot 1's on the host) feeds the world's partner
     *  mirror, any further seat feeds the extra mirrors, so three and four pilots read the
     *  same way; SWARM is the host's alone; PAIN flows out of the hub and listeners drop
     *  what isn't for their seat; a TAKE is for the hub. It is never a document error to
     *  drop a plane. */
    public static void apply(Session s, World w, int tag, int sender, byte[] body, int off, int len) {
        if (sender == s.myId()) return; // echoes: never feed yourself
        boolean host = s.isHost();
        switch (tag) {
            case SHIP:
                // the partner is the senior hull (the host's on a guest, pilot 1's on the host);
                // any further seat lands on the extra mirrors — three and four read the same way
                if (w.clientSeat ? sender == 0 : sender == 1) {
                    applyPartner(w, body, off, len);
                    w.partnerSeat = sender; // whose hull the mirror speaks for
                } else {
                    applyExtra(w, sender, body, off, len);
                }
                break;
            case SHOT:
                applyShot(w, body, off, host ? sender : -1);
                break;
            case SWARM:
                if (!host && sender == 0) applySwarm(w, body, off, len);
                break;
            case PAIN: {
                if (host) break; // pains are born in the hub; other sends are stray
                if ((body[off] & 0xFF) != s.myId()) break; // another seat's word passes me by
                applyPain(w, body, off);
                break;
            }
            case TAKE:
                if (host) { // retire the swept pickup; the next swarm carries the absence
                    int id = Bin.getU8(body, off) | Bin.getU8(body, off + 1) << 8;
                    for (int i = 0; i < w.pickups.size(); i++)
                        if (w.pickups.get(i).netId == id) {
                            w.pickups.remove(i);
                            break;
                        }
                }
                break;
            case ASTRA:
                if (host) applyAstra(w, sender, body, off, len); // a guest's release, resolved here
                break;
        }
    }

    /** Any seat's pose in, aged: the pose was born on the sender's clock; everyone runs the
     *  same fixed-step game clock, so the difference is the pose's air time. Integrating
     *  the stale velocity over that age lands the hull where it is now, not where it was.
     *  The partner hull keeps its long-held fields on the world; the extra seats share the
     *  same law through their SeatHull mirrors. */
    static void applyPartner(World w, byte[] body, int o, int len) {
        int defIdx = body[o] & 0xFF;
        float frac = (body[o + 1] & 0xFF) / 255f;
        if (w.partner == null || w.partnerShipIdx != defIdx) {
            w.partner = new Ship(Bin.getFloat(body, o + 2), Bin.getFloat(body, o + 6),
                    ShipDef.ALL[defIdx], w.hangar);
            w.partnerShipIdx = defIdx;
        }
        applyPose(w.partner, w, body, o, len, frac);
    }

    static void applyExtra(World w, int seat, byte[] body, int o, int len) {
        World.SeatHull x = w.extra(seat);
        if (x == null) { x = new World.SeatHull(seat); w.extras.add(x); }
        int defIdx = body[o] & 0xFF;
        float frac = (body[o + 1] & 0xFF) / 255f;
        if (x.ship == null || x.art != defIdx) {
            x.ship = new Ship(Bin.getFloat(body, o + 2), Bin.getFloat(body, o + 6),
                    ShipDef.ALL[defIdx], w.hangar);
            x.art = defIdx;
        }
        applyPose(x.ship, w, body, o, len, frac);
    }

    /** Shared pose lift, exact numbers for both mirror kinds. */
    static void applyPose(Ship p, World w, byte[] body, int o, int len, float frac) {
        p.x = Bin.getFloat(body, o + 2);
        p.y = Bin.getFloat(body, o + 6);
        p.vx = Bin.getFloat(body, o + 10);
        p.vy = Bin.getFloat(body, o + 14);
        float age = Math.max(0, Math.min(0.25f, w.clock - Bin.getFloat(body, o + 18)));
        p.x += p.vx * age; // the flight time the pose aged through on the wire
        p.y += p.vy * age;
        p.hp = frac * p.maxHp();
        p.iFrames = (body[o + 22] & 0xFF) != 0 ? 0.35f : 0;
        if (len >= SHIP_FIELDS) { // the pilot's heading; an older client without it keeps the last
            float face = Bin.getFloat(body, o + 23);
            if (Float.isNaN(p.faceGoal)) p.facing = face; // the first packet snaps, the rest ease
            p.faceGoal = face;
        }
    }

    /** A round anyone fired: its owner credits the kill it lands (host side only). */
    static void applyShot(World w, byte[] body, int o, int owner) {
        Bullet b = w.obtainBullet();
        b.hostile = (body[o] & 0xFF) != 0;
        b.x = Bin.getFloat(body, o + 1);
        b.y = Bin.getFloat(body, o + 5);
        b.vx = Bin.getFloat(body, o + 9);
        b.vy = Bin.getFloat(body, o + 13);
        b.damage = Bin.getFloat(body, o + 17);
        b.radius = Bin.getFloat(body, o + 21);
        b.r = (body[o + 25] & 0xFF) / 255f;
        b.g = (body[o + 26] & 0xFF) / 255f;
        b.b = (body[o + 27] & 0xFF) / 255f;
        b.life = 3f;   // a round shot out of sight simply fades
        b.owner = owner; // who the kill goes to (the host's ledger only cares)
        b.def = null;  // no pattern key travelled: the tinted circle fallback draws it
        b.dead = false; // pool reuse: the round arrives fresh
        b.aoe = 0;      // ghosts resolve nothing themselves; combat is the host's word
        b.homingTurn = 0;
        b.pierceWalls = b.pierceEnemies = false;
        w.bullets.add(b);
    }

    static void applySwarm(World w, byte[] body, int o, int len) {
        w.hostFlags = (byte) (body[o] & 0xFF);
        int rows = (body[o + 1] & 0xFF) | (body[o + 2] & 0xFF) << 8;
        int r = o + 3;
        for (int i = 0; i < rows && r + FOE_FIELDS <= o + len; i++) {
            w.ghostAt((body[r] & 0xFF) | (body[r + 1] & 0xFF) << 8,
                    Bin.getFloat(body, r + 2), Bin.getFloat(body, r + 6),
                    body[r + 10] & 0xFF, body[r + 11] & 0xFF);
            r += FOE_FIELDS;
        }
        int loot = (body[r] & 0xFF) | (body[r + 1] & 0xFF) << 8;
        r += 2;
        for (int i = 0; i < loot && r + LOOT_FIELDS <= o + len; i++) {
            w.lootGhost((body[r] & 0xFF) | (body[r + 1] & 0xFF) << 8,
                    body[r + 2] & 0xFF, body[r + 3] & 0xFF,
                    Bin.getFloat(body, r + 4), Bin.getFloat(body, r + 8));
            r += LOOT_FIELDS;
        }
        w.expireMissingGhosts();
    }

    /** A guest's release named the foes it aimed at: the hub arms the same volley there, so
     *  the Astra is a real weapon on every seat and the kill pays the pilot who fired it. */
    static void applyAstra(World w, int sender, byte[] body, int o, int len) {
        int count = body[o] & 0xFF;
        int r = o + 1;
        int[] ids = new int[count];
        for (int i = 0; i < count && r + 2 <= o + len; i++) {
            ids[i] = (body[r] & 0xFF) | (body[r + 1] & 0xFF) << 8;
            r += 2;
        }
        w.astraFor(sender, ids);
    }

    static void applyPain(World w, byte[] body, int o) {
        int kind = body[o + 1] & 0xFF;
        long a = Bin.getLong(body, o + 2);
        float b = Bin.getFloat(body, o + 10);
        float c = Bin.getFloat(body, o + 14);
        w.pain(a, kind, b, c);
    }
}
