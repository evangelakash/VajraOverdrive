package vajra.world;

import vajra.audio.Sfx;
import vajra.audio.Audio;

import vajra.enemy.Brain;
import vajra.enemy.Enemy;
import vajra.ship.Hangar;
import vajra.ship.Ship;
import vajra.ship.ShipControls;
import vajra.ship.ShipDef;
import vajra.weapon.Bullet;
import vajra.weapon.WeaponDef;

import com.badlogic.gdx.utils.Pool;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class World {
    public float width = 5200;
    public float height = 20600;

    public Ship ship;
    public final Hangar hangar = new Hangar();
    public TileMap tileMap;
    public TileMap tileMapR2;
    public TileMap tileMapR3;
    public TileMap tileMapR4;
    public TileMap tileMapBoss;
    public Orb[] orbs = new Orb[6];
    public Checkpoint[] checkpoints = new Checkpoint[5]; // Atala bands 1-5: Bhu Loka has none
    public Checkpoint respawnCp;    // most recent collected checkpoint; null = region entrance
    public int lives = 3;
    public static final int MAX_LIVES = 5; // a forged release can lift a run back to this, never past
    public boolean gameOver;
    /** This run was flown with other pilots: it folds into the multiplayer book, not the
     *  campaign one. */
    public boolean multiplayer;
    /** This run has already been written into the pilot's book — the run screen and the
     *  pause screen both end a run, and only the first of them counts. */
    public boolean recorded;
    public Enemy boss;              // Bala: null until the kill quota lifts the seal, then in the pit
    public boolean bossDead;
    public boolean won;         // Bala fell: the run ends in victory (after the banner lap)
    public static final float VICTORY_LAP = 4f; // seconds the world still runs after the kill, banner up

    /** THE WORLD AXIS: every gate, the court, shops and the boss arena pour out of x = 2600 —
     *  the single spine the map is mirrored about. Brains, HUD compass and gen all read this. */
    public static final float WORLD_CX = 2600f;
    /** The boss arena's heart: the pit Bala holds, and the kill ring below the banner. */
    public static final float PIT_X = 2600f, PIT_Y = 19104f;

    /** The run is over: dead, the blade whole, or Bala fallen and the banner lap done. A
     *  guest's seat also ends when the host's run does — the swarm carries that bit. */
    public boolean runEnded() {
        return gameOver || vajraDone || (won && victoryT >= VICTORY_LAP)
                || (clientSeat && (hostFlags & 1) != 0);
    }
    public boolean vajraDone;       // all 7 Vajrayudha bands recovered: the run is over
    public float victoryT = -1;     // seconds since the kill; -1 = no victory yet
    public static final float BOSS_WAKE_Y = 18000; // cross this line and the commander calls his ring
    public final int[] kills = new int[Enemy.Kind.values().length]; // player kills per kind
    public final int[] quota = new int[Enemy.Kind.values().length]; // kills Bala demands per kind
    public final Score score = new Score(); // points + combo: the run's ledger
    public final Popups popups = new Popups(); // floating text: score payments, combo tiers
    // A cache per Atala band: touch it and the local squad converges; kill them and it opens.
    // Never drawn — the player is not meant to learn where one is (see README: production must
    // keep caches invisible).
    // CACHE STATE IS PER PLAYER, not per world: two ships in one band each get their own
    // stash, so co-op players cannot open each other's. Solo is slot 0.
    public static final int CACHE_SEALED = 0, CACHE_CONTESTED = 1, CACHE_DONE = 2;
    public static final int SLOTS = 4; // co-op seats; only slot 0 is populated today
    public float[][] caches = new float[5][2];
    public int[][] cacheState = new int[SLOTS][5];
    public int slot; // which seat this run's ship occupies
    // LIVE CO-OP (docs/MULTIPLAYER.md, phase 3): the other pilot is a ghost kept by packets;
    // my hull is authoritative on my machine. Foes and loot are the host's truth, streamed.
    public Ship partner;
    public int partnerShipIdx = -1; // their hull art, so a SHIP packet rebuilds it on change
    public int partnerSeat = 1; // the partner's roster id; their SHIP packets stamp it

    /** A remote hull beyond pilot 1 — same mirror law, one seat each. */
    public static class SeatHull {
        public final int seat;    // the roster id whose word this hull is
        public Ship ship;
        public int art = -1;
        public SeatHull(int seat) { this.seat = seat; }
    }

    /** Seats above pilot 1 land here; they fly the same beams, pains and bullets the partner does. */
    public final ArrayList<SeatHull> extras = new ArrayList<>();

    /** The hull mirror of that seat, or null until its first SHIP packet lands. */
    public SeatHull extra(int seat) {
        for (SeatHull x : extras) if (x.seat == seat) return x;
        return null;
    }
    public boolean clientSeat;  // guests run only their own hull; the host runs everything else
    public byte hostFlags;      // the swarm's run bits: bit0 host run over, bit1 host won

    /** This machine flies a GUEST seat: its own hull only, the fight as ghosts. The local
     *  garrison is dropped (the world is built with the whole roster standing in it, and a
     *  guest must see the fight ONCE, as the host's ghosts — not twice). The loot id space is
     *  pushed to the high half so a guest's own drop can never collide with the host's: a
     *  TAKE carries an id and the hub retires what it finds under it. */
    public void seatAsClient() {
        clientSeat = true;
        dropSeq = 0x8000;
        enemies.clear();
        boss = null;
    }
    public final java.util.ArrayList<Integer> takes = new java.util.ArrayList<>(); // my sweeps to retire
    public ShotSink shotWire;   // the spawn chokes fire rounds through it; the net pump sets it
    public PainSink painWire;   // a partner hull touched, shoved or a kill paying their book
    /** The Astra's wire: a guest seat announces the foes it aimed at and the hub arms the
     *  same volley there, so the signature weapon is a real weapon on every seat. */
    public interface AstraSink { void astra(int[] targetIds, int count); }
    public AstraSink astraWire;
    public float shipBeat, swarmBeat; // this seat's send-cadence debt
    public int shooter;         // whose round/contact is about to hurt someone: a roster id
    int enemySeq, dropSeq;      // the host's id ledgers for foes and ground loot
    public java.util.ArrayList<Enemy> enemies = new java.util.ArrayList<>();
    public final List<Bullet> bullets = new ArrayList<>();
    public final List<Effect> effects = new ArrayList<>();
    public final List<Pickup> pickups = new ArrayList<>(); // coins + health pops, swept by the hull
    public static final float KNOCK = 260;  // contact impulse, px/s, applied to both bodies
    public static final float RAM = 10;     // damage the enemy eats per contact touch
    public static final float RAM_CD = 0.5f;// seconds between rams on the same enemy
    // LOOT tables live in LootSystem.
    // Pancha Tatva in pancha order (LOKA-LAWS §2). WHICH tatva each loka pays — and the
    // difficulty curve a run is graded against — live in LokaTable.
    public static final String[] TATVA = { "Akasha", "Vayu", "Agni", "Jala", "Prithvi" };
    public static final float[][] TATVA_RGB = {
            { 0.72f, 0.64f, 1.00f }, { 0.86f, 0.94f, 1.00f }, { 1.00f, 0.45f, 0.10f },
            { 0.25f, 0.82f, 0.90f }, { 0.82f, 0.66f, 0.28f } };
    public int lokaIndex = 0; // which loka this run is in (LokaTable index; 0 = Atala)
    public static final float PULL_R = 90;   // magnetism reach, px
    public static final float PULL_G = 1500; // accel at 100% magnet: terminal = PULL_G / DRAG = 500 px/s

    private final Pool<Bullet> bulletPool = new Pool<Bullet>(64, 512) {
        @Override
        protected Bullet newObject() {
            return new Bullet();
        }
    };

    final Random orbRng;
    public final Random fxRng = new Random(2107); // particles only (public: Ship smoke rolls); orb/blink rolls stay pure
    Random lootRng;

    /** A run's chaos, seeded. Seed 0 keeps the legacy constants — every existing test and
     *  solo flight builds the world it always did. A multiplayer session seeds its shadow
     *  runs with one number so every pilot races the same orb spots, loot tables and boss
     *  blinks (terrain is fixed hand-tuned content; it does not vary per seed). */
    public World(long seed) {
        orbRng = seed == 0 ? new Random(21) : new Random(seed ^ 0x004F21L);
        lootRng = seed == 0 ? new Random(90210) : new Random(seed ^ 0x90210L);
        ship = new Ship(width / 2, 800, ShipDef.ALL[0], hangar);
        new WorldGen(this).build();
        docking = new DockingSystem(this);
        cacheDirector = new CacheDirector(this);
        enemyDirector = new EnemyDirector(this);
    }

    public World() {
        this(0);
    }

    private final DockingSystem docking;
    private final CacheDirector cacheDirector;
    private final LootSystem loot = new LootSystem();
    private final EnemyDirector enemyDirector;

    public String[] regionNames = { "Avatarana", "Svarna Dvara", "Maya Vana", "Svapna Mandala", "Bala Mukha", "Boss: Bala" };
    public static final float ATALA_Y = 1600; // the grand gate: below = Bhu Loka, at/above = Atala
    public float[] regionStart = { 0, ATALA_Y, 4100, 7600, 12000, 17600 };
    public float[] regionEnd = { 1600, 4100, 7600, 12000, 17600, 20600 };
    public float[][] shops = new float[4][2]; // Atala bands 1-4: none in Bhu, none in the boss band
    public int[] shopState = new int[shops.length];
    public float[] shopTimer = new float[shops.length];
    public static final int UNDOCKED = 0, DOCKING = 1, DOCKED = 2, SITTING = 3;
    public static final float DOCK_TIME = 0.6f; // the doc1..doc6 sequence: 6 frames × 0.1s

    public float[][] torches; // {anchorX, anchorY, angle, sampleX, sampleY}: wall face it sits on
    public float[][] plants;  // {x, y, variant, phase, angle, size, sampleX, sampleY}: base anchored on rock

    /** The world clock: drives enemy orbits, dust and every animation in one place. */
    public float clock; // sim seconds since the run began; the wire stamps SHIP poses with it

    // SCORE: band crossings paid once per run (dying back into an earlier band never re-pays).
    private boolean[] regionScored;
    private int lastRegion;

    /** True when the WHOLE hull sits inside the marked bay under shop i. */
    public boolean inDockBox(int i, float x, float y) {
        return docking.inDockBox(i, x, y);
    }

    /** A docking just finished — the screen opens the hangar on this. */
    public boolean shopReady() {
        return docking.shopReady();
    }

    /** Closing the hangar parks that bay as SITTING, so it cannot reopen until you fly out. */
    public void closeDocked() {
        docking.closeDocked();
    }

    public void update(float dt, ShipControls input) {
        if (clientSeat) { // a guest's seat: my hull, my shots, the swarm as ghosts (see docs)
            updateSeat(dt, input);
            return;
        }
        if (victoryT >= 0) victoryT += dt;
        shooter = 0; // this frame's hurts are the host's own unless a round claims otherwise
        if (forgeCd > 0) forgeCd -= dt;
        int chain = score.combo;
        score.update(dt);
        if (chain >= 5 && score.combo == 0) // a chain worth missing just dropped
            popups.push(new Popups.Spec().text("COMBO LOST").img("shanti").at(ship.x, ship.y + 30)
                    .color(1f, 0.35f, 0.3f).scale(1.2f).life(1f).intro(0));
        popups.update(dt);
        if (boss == null) enemyDirector.trySpawnBoss(); // the frame the quota fills, he is simply there
        ship.update(dt, input, this);
        regionEntry();
        for (Orb o : orbs) o.update(dt, this, orbRng);
        clock += dt;
        if (partner != null) glideHull(partner, dt); // the partner glides between packets
        for (SeatHull x : extras) if (x.ship != null) glideHull(x.ship, dt);
        // index loop: a summon (boss blink) may append to the roster mid-iteration
        for (int i = 0; i < enemies.size(); i++) enemies.get(i).update(dt, this, clock);
        updateAstra(dt); // the forged weapon's cubes, after the roster has moved
        // Contact: each body shoves the other away along the contact line, opposite directions.
        // Impulses are velocity kicks — ship friction and Brain steering bleed them off naturally.
        for (Enemy e : enemies) contact(e);
        for (int i = bullets.size() - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.update(this, dt);
            if (!b.dead) collide(b);
            if (b.dead) {
                bullets.remove(i);
                bulletPool.free(b);
            }
        }
        for (int i = enemies.size() - 1; i >= 0; i--) if (enemies.get(i).dead) enemies.remove(i);
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect e = effects.get(i);
            e.update(dt);
            if (e.done()) effects.remove(i);
        }
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            // LIFETIME: uncollected loot used to sit in the world forever, so a full clear
            // buried the band in coins and a health pop at a full hull floated until the end
            // of time. Everything fades out on its own now.
            p.life -= dt;
            if (p.life <= 0) { pickups.remove(i); continue; }
            // MAGNETISM: a faint tug toward the hull inside PULL_R, force scaled by the hangar's
            // magnet power. Coins, health, shards, pieces — everything gets it.
            float mx = ship.x - p.x, my = ship.y - p.y;
            float m2 = mx * mx + my * my;
            if (m2 < PULL_R * PULL_R && m2 > 4) {
                float inv = 1 / (float) Math.sqrt(m2);
                p.vx += mx * inv * hangar.magnet * PULL_G * dt;
                p.vy += my * inv * hangar.magnet * PULL_G * dt;
            }
            p.update(dt);
            float dx = ship.x - p.x, dy = ship.y - p.y;
            if (dx * dx + dy * dy > Pickup.COLLECT_R * Pickup.COLLECT_R) continue;
            if (p.type == Pickup.HEALTH && ship.hp >= ship.maxHp()) continue; // full hull: it floats on
            loot.collect(this, p);
            pickups.remove(i);
        }
        for (Checkpoint c : checkpoints) collectCheckpoint(c);
        cacheDirector.update();
        cacheDirector.resolve();
        docking.update(dt); // after ship.update, so the bay test sees this frame's position
    }

    /** A checkpoint under the hull: taken, scored, and the new respawn anchor. The guest seat
     *  runs the same test — the checkpoints are the same geometry on every machine, so a guest
     *  anchors on the same stone the host did. */
    private void collectCheckpoint(Checkpoint c) {
        if (c.collected || !c.touches(ship.x, ship.y)) return;
        c.collected = true;
        sound(Sfx.CHECKPOINT);
        respawnCp = c;
        score.add(Score.CHECKPOINT);
        popups.push(new Popups.Spec().text("ANCHORED").img("pratishtha")
                .at(c.x, c.y + 30).color(0.5f, 0.9f, 1f).scale(1.1f));
        addEffect(Effect.ring(c.x, c.y, 40, 1f, 0.45f, 0.72f));
    }

    // ---- the guest seat ----------------------------------------------------------------------

    /** The client machine's run in full: my hull, my shots, my use of the land, and the
     *  swarm's foes and loot as gliding ghosts. Damage and kills travel as PAINs instead
     *  of impulses — my machine owns everything about my hull; the host owns the fight. */
    private void updateSeat(float dt, ShipControls input) {
        int chain = score.combo;
        score.update(dt);
        if (chain >= 5 && score.combo == 0) // a chain worth missing just dropped
            popups.push(new Popups.Spec().text("COMBO LOST").img("shanti").at(ship.x, ship.y + 30)
                    .color(1f, 0.35f, 0.3f).scale(1.2f).life(1f).intro(0));
        popups.update(dt);
        ship.update(dt, input, this);
        regionEntry();
        guestCheckpoints();
        for (Orb o : orbs) o.update(dt, this, orbRng); // the same drift the host's orbs take
        if (partner != null) glideHull(partner, dt); // the partner glides between packets
        for (SeatHull x : extras) if (x.ship != null) glideHull(x.ship, dt);
        clock += dt;
        updateAstra(dt);
        ghostGlide(dt);
        // THE JOINER'S LEAK: the host loop ages and retires its effects, this seat did not, so
        // every exhaust puff, smoke wisp, ring and spark this machine added stayed on screen
        // for ever (the smoke that never went away) and the list grew every frame (the lag).
        for (int i = effects.size() - 1; i >= 0; i--) {
            Effect e = effects.get(i);
            e.update(dt);
            if (e.done()) effects.remove(i);
        }
        for (int i = bullets.size() - 1; i >= 0; i--) { // flight and expiry only; combat waits for the swarm
            Bullet b = bullets.get(i);
            b.update(this, dt);
            if (b.dead) {
                bullets.remove(i);
                bulletPool.free(b);
            }
        }
        for (int i = pickups.size() - 1; i >= 0; i--) { // my magnet, my sweep; the host is told
            Pickup p = pickups.get(i);
            float mx = ship.x - p.x, my = ship.y - p.y;
            float m2 = mx * mx + my * my;
            if (m2 < PULL_R * PULL_R && m2 > 4) {
                float inv = 1 / (float) Math.sqrt(m2);
                p.vx += mx * inv * hangar.magnet * PULL_G * dt;
                p.vy += my * inv * hangar.magnet * PULL_G * dt;
            }
            p.update(dt);
            float dx = ship.x - p.x, dy = ship.y - p.y;
            if (dx * dx + dy * dy > Pickup.COLLECT_R * Pickup.COLLECT_R) continue;
            if (p.type == Pickup.HEALTH && ship.hp >= ship.maxHp()) continue; // full hull: it floats on
            loot.collect(this, p);
            takes.add(p.netId); // the hub retires that drop; the next swarm carries the absence
            pickups.remove(i);
        }
        docking.update(dt);
    }

    /** The guest seat's checkpoints: the same stones the host anchors on (the world is the
     *  same geometry on every machine), so a guest respawns where the crew respawns. */
    private void guestCheckpoints() {
        for (Checkpoint c : checkpoints) collectCheckpoint(c);
    }

    /** A mirrored hull between packets: it coasts on its last velocity and turns toward the
     *  facing its pilot last reported, so the other pilot's nose is visible and moves smoothly. */
    private static void glideHull(Ship h, float dt) {
        h.x += h.vx * dt;
        h.y += h.vy * dt;
        if (!Float.isNaN(h.faceGoal))
            h.facing += wrapAngle(h.faceGoal - h.facing) * (1f - (float) Math.exp(-18f * dt));
    }

    /** Ghost render objects glide toward their last snapshot truth (10 Hz set, 60 Hz flow). */
    private void ghostGlide(float dt) {
        float k = 1f - (float) Math.exp(-20f * dt);
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy g = enemies.get(i);
            if (!g.ghost) continue;
            if (g.gone) {
                enemies.remove(i);
            } else {
                if (clock - g.gAt < 0.25f) { // coast on the last truth until the next arrives
                    g.gx += g.gvx * dt;
                    g.gy += g.gvy * dt;
                }
                g.x += (g.gx - g.x) * k;
                g.y += (g.gy - g.y) * k;
            }
        }
        for (Pickup p : pickups) {
            p.x += (p.gx - p.x) * k;
            p.y += (p.gy - p.y) * k;
        }
    }

    /** The swarm's foe rows: keyed on the host's ids, each ghost gliding to the newest truth.
     *  The flavour's low 5 bits name the Kind, the top three the boss / elite / rank tags;
     *  hpFrac is the host's read of the foe's armour, so the guest's bar is honest. */
    public void ghostAt(int id, float x, float y, int flavour, int hpFrac) {
        for (Enemy e : enemies) {
            if (e.ghost && e.netId == id) {
                e.gone = false;
                float span = clock - e.gAt; // snapshot to snapshot: the foe's real velocity
                if (span > 0.03f && span < 0.5f) {
                    e.gvx += ((x - e.gx) / span - e.gvx) * 0.6f;
                    e.gvy += ((y - e.gy) / span - e.gvy) * 0.6f;
                }
                e.gAt = clock;
                e.gx = x;
                e.gy = y;
                e.seen = true;
                e.hp = e.maxHp * (hpFrac / 255f);
                e.dispHp = e.hp;
                return;
            }
        }
        Enemy e = new Enemy(Enemy.Kind.values()[flavour & 0x1F], x, y);
        e.ghost = true;
        e.netId = id;
        e.boss = (flavour & 0x20) != 0;
        e.elite = (flavour & 0x40) != 0;
        if ((flavour & 0x80) != 0) e.rank = 1;
        e.active = true; // ghosts never wake; they start at the swarm's truth
        e.hp = e.maxHp * (hpFrac / 255f);
        e.dispHp = e.hp;
        e.gx = x;
        e.gy = y;
        e.gAt = clock;
        e.seen = true;
        enemies.add(e);
        if (e.boss && boss == null) boss = e; // the warden shows on the guest's bar too
    }

    /** Loot rows: keyed the same way, riding the ghosts' glide. */
    public void lootGhost(int id, int type, int variant, float x, float y) {
        for (Pickup p : pickups) {
            if (p.netId == id) {
                p.gx = x;
                p.gy = y;
                p.seen = true;
                return;
            }
        }
        Pickup p = new Pickup();
        p.netId = id;
        p.type = type;
        p.variant = variant;
        p.x = x;
        p.y = y;
        p.gx = x;
        p.gy = y;
        p.seen = true;
        pickups.add(p);
    }

    /** Everything the last swarm failed to mention is gone on the host too. */
    public void expireMissingGhosts() {
        for (int i = enemies.size() - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            if (!e.ghost) continue;
            if (!e.seen) enemies.remove(i);
            else e.seen = false;
        }
        for (int i = pickups.size() - 1; i >= 0; i--) {
            Pickup p = pickups.get(i);
            if (!p.seen) pickups.remove(i);
            else p.seen = false;
        }
    }

    /** The hub has spoken about my seat: the hull or the book feels it here, immediately. */
    public void pain(long bits, int kind, float b, float c) {
        switch (kind) {
            case PAIN_TOUCH: // a partner's round touched me; my shields and armour answer locally
                ship.damage(b, this);
                break;
            case PAIN_SHOVE: // contact: the line's kick is what the push gives me
                ship.vx += b;
                ship.vy += c;
                break;
            case PAIN_KILL: // my round landed: my chain, my rate; the land's popup is news anyway
                Enemy.Kind k = Enemy.Kind.values()[(int) (bits & 0xFF)];
                boolean boss = (bits & 0x100) != 0, elite = (bits & 0x200) != 0, lead = (bits & 0x400) != 0;
                int tier = boss ? 3 : elite ? 2 : lead ? 1 : 0;
                score.combo++;
                score.comboT = Score.WINDOW;
                long pay = (long) (Score.TIER[tier] + Math.round(k.hp));
                score.add(pay);
                popups.push(new Popups.Spec().text("+" + pay).at(b, c + 10)
                        .color(1f, 215 / 255f, 0).scale(boss || elite ? 1.4f : 1f));
                break;
            default:
                break;
        }
    }

    // ---- co-op plumbing both seats read ------------------------------------------------------

    /** Is the partner hull flying? Its hp is its pilot's word, arriving on the SHIP packets. */
    public boolean partnerAlive() {
        return partner != null && partner.hp > 0;
    }

    /** The brains' targeting eye: whichever hull is nearest their mischief. */
    public Ship nearestHull(float x, float y) {
        Ship best = ship;
        float bestD2 = (x - ship.x) * (x - ship.x) + (y - ship.y) * (y - ship.y);
        if (partnerAlive()) {
            float d2 = (x - partner.x) * (x - partner.x) + (y - partner.y) * (y - partner.y);
            if (d2 < bestD2) { best = partner; bestD2 = d2; }
        }
        for (SeatHull gh : extras) { // the extra pilots' hulls fly into their fire too
            if (gh.ship == null || gh.ship.hp <= 0) continue;
            float d2 = (x - gh.ship.x) * (x - gh.ship.x) + (y - gh.ship.y) * (y - gh.ship.y);
            if (d2 < bestD2) { best = gh.ship; bestD2 = d2; }
        }
        return best;
    }

    /** The deepest hull crossing of the wake line — a boss wakes on any hull, not one. */
    public float deepestHullY() {
        float top = ship.y;
        if (partnerAlive() && partner.y > top) top = partner.y;
        for (SeatHull gh : extras)
            if (gh.ship != null && gh.ship.hp > 0 && gh.ship.y > top) top = gh.ship.y;
        return top;
    }

    /** Something the host did to my hull or my kill book, born in {@code countKill}/collide. */
    public static class Pain {
        public int target, kind;
        public long a;
        public float b, c;
    }

    public interface PainSink {
        void pain(Pain p);
    }

    /** A round the sim birthed: the wire relays it, def-less, and both skies match. */
    public static class Shot {
        public float x, y, vx, vy, damage, radius, r, g, b;
        public boolean hostile;
    }

    public interface ShotSink {
        void shot(Shot s);
    }

    public static final int PAIN_TOUCH = 0, PAIN_SHOVE = 1, PAIN_KILL = 2;

    /** Which loka each band belongs to (index into LokaTable). Atala holds regions 0-5, so
     *  every band of the current build is loka 0; the next loka claims regions 6 onwards.
     *  This is the mapping the HUD's region names and the economy's "which element pays"
     *  both read — bands are NOT lokas, and treating them as one is how Atala would end up
     *  paying Vayu in its own boss arena. */
    public int[] lokaOfRegion = { 0, 0, 0, 0, 0, 0 };

    /** SCORE: the first crossing into a band pays once per run. The spawn band is pre-scored
     *  (you start there), and however many times a death throws you back, already-seen bands
     *  stay silent. Sized from regionStart, so a future loka's bands cost nothing. */
    private void regionEntry() {
        if (regionScored == null) {
            regionScored = new boolean[regionStart.length];
            regionScored[0] = true;
        }
        int r = currentRegionIndex();
        // the deepest loka reached IS the loka: it decides which tatva the drops pay, which
        // hulls the hangar will sell, and how mean the roster is. Best-run-so-far, so a death
        // back down a band never un-sells anything or re-lights a tatva you already fed.
        int loka = lokaOfRegion[Math.min(r, lokaOfRegion.length - 1)];
        if (loka > lokaIndex) {
            lokaIndex = loka;
            hangar.loka = lokaIndex;
        }
        if (r == lastRegion) return;
        lastRegion = r;
        sound(Sfx.REGION); // a gate crossed into a new band
        if (!regionScored[r]) {
            regionScored[r] = true;
            score.add(Score.REGION);
            popups.push(new Popups.Spec().text("THE DEEPENING").img("pravesh")
                    .at(ship.x, ship.y + 40).color(1f, 1f, 1f).scale(1.2f).life(1.8f));
        }
    }

    /** What a death pays: the whole purse leaves as ONE fanned burst. Called from Enemy.hurt —
     *  retires and stand-downs never pay. */
    /** A sound in the world, heard from the ship: quieter with distance, placed left or right by
     *  where it happened. The bus is silence in every headless test. */
    public void sound(Sfx s, float x, float y) {
        Audio.at(s, x, y, ship.x, ship.y);
    }

    /** A sound with no place: the ship's own voice, a pickup under the hull. */
    public void sound(Sfx s) {
        Audio.ui(s);
    }

    public void dropLoot(Enemy e) {
        loot.drop(this, lootRng, e);
    }

    /** The cache fights back: the local squad within reach converges on the ship — site guards
     *  stay put, and so does everything outside the radius. */
    public void touchCache(int i) {
        cacheDirector.touch(i);
    }

    /** A contested cache stays shut while any of its squad still lives. */
    public void resolveCaches() {
        cacheDirector.resolve();
    }

    /** Death drops you at the most recent collected checkpoint, else the region entrance. */
    public float[] respawnAnchor() {
        if (respawnCp != null) return new float[] { respawnCp.x, respawnCp.y };
        ANCHOR[0] = width / 2;
        ANCHOR[1] = regionStart[currentRegionIndex()] + 120;
        return ANCHOR;
    }

    private static final float[] ANCHOR = new float[2];

    boolean spotClear(float x, float y) {
        return !blockedPx(x, y) && !blockedPx(x + 40, y) && !blockedPx(x - 40, y)
                && !blockedPx(x, y + 40) && !blockedPx(x, y - 40);
    }

    /** Resolves one live shot against whatever it is meant to hit. */
    private void collide(Bullet b) {
        if (b.hostile) {
            hullHit(b); // my hull, and the partner's in co-op
            return;
        }
        // BULLET DUEL: a player round trades with hostile fire it can beat. Equal damage
        // cancels both (same power, same cancellation); a round that beats the shot kills it;
        // a round that can't beat it is spent for nothing and the shot flies on. The trade
        // always costs the round, so clearing fire is never free — and light weapons (Light
        // MG, a weak charge) can't touch heavy shots at all. A splash round goes off where
        // it died: an intercepted bomb still explodes, and eats the shot that stopped it.
        for (Bullet o : bullets) {
            if (!o.hostile || o.dead) continue;
            if (Math.abs(b.x - o.x) > b.radius + o.radius) continue;
            if (Math.abs(b.y - o.y) > b.radius + o.radius) continue;
            if (b.damage >= o.damage) o.dead = true;
            if (b.aoe > 0) b.detonate(this);
            else b.dead = true;
            break;
        }
        if (b.dead) return;
        shooter = b.owner < 0 ? 0 : b.owner; // an unstamped round is this seat's own; relays carry ids
        for (Enemy e : enemies) {
            if (e.dead) continue;
            if (Math.abs(b.x - e.x) > e.w / 2 + b.radius) continue;
            if (Math.abs(b.y - e.y) > e.h / 2 + b.radius) continue;
            if (b.aoe > 0) { // splash: bursts on contact, never a plain hit
                b.detonate(this);
                return;
            }
            boolean killed = e.hurt(b.damage, this);
            spray(b.x, b.y, b.vx, b.vy, b.r, b.g, b.b); // sparks thrown along the shot's path
            if (!b.pierceEnemies || killed) {
                b.dead = true;
                break;
            }
        }
    }

    /** Hostile fire against a hull: mine answers locally, an owned hull's pilot answers. */
    private void hullHit(Bullet b) {
        int hulls = 2 + extras.size();
        for (int i = 0; i < hulls; i++) {
            SeatHull gh = i >= 2 ? extras.get(i - 2) : null;
            Ship h = i == 0 ? ship : i == 1 ? partner : gh.ship;
            if (h == null || h.iFrames > 0) continue; // dash: the ship ghosts, shots pass through
            if (Math.abs(b.x - h.x) < Ship.W / 2f + b.radius
                    && Math.abs(b.y - h.y) < Ship.H / 2f + b.radius) {
                if (h == ship) h.damage(b.damage, this);
                else { // the hull is their truth: their damage code will have felt it exactly
                    h.hp = Math.max(0, h.hp - b.damage); // the mirror's bar moves until the next word
                    if (painWire != null) {
                        Pain p = new Pain();
                        p.target = gh != null ? gh.seat : partnerSeat;
                        p.kind = PAIN_TOUCH;
                        p.b = b.damage;
                        painWire.pain(p);
                    }
                }
                b.dead = true;
                break;
            }
        }
    }

    /** Contact against one live enemy for every hull that could ram it. */
    private void contact(Enemy e) {
        body(e, ship, 0);
        if (partnerAlive()) body(e, partner, partnerSeat);
        for (SeatHull gh : extras) { // the extras ram too, and their shoves go home
            if (gh.ship == null || gh.ship.hp <= 0) continue;
            body(e, gh.ship, gh.seat);
        }
    }

    private void body(Enemy e, Ship h, int seat) {
        if (e.dead || e.bossHidden) return; // mid-blink: no body to shove or ram
        float dx = h.x - e.x, dy = h.y - e.y;
        if (Math.abs(dx) >= Ship.W / 2 + e.w / 2 || Math.abs(dy) >= Ship.H / 2 + e.h / 2) return;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) { dx = 0; dy = 1; len = 1; } // dead centre: shove straight up
        if (h == ship) { // MY shove is mine; a remote hull's is a message home
            h.vx += dx / len * KNOCK;
            h.vy += dy / len * KNOCK;
        } else if (painWire != null) {
            Pain p = new Pain();
            p.target = seat;
            p.kind = PAIN_SHOVE;
            p.b = dx / len * KNOCK;
            p.c = dy / len * KNOCK;
            painWire.pain(p);
        }
        e.vx -= dx / len * KNOCK; // both bodies shove back: the enemy eats the same answer
        e.vy -= dy / len * KNOCK;
        e.recover = Enemy.RECOVER; // straight home for 2s, kind flair paused
        if (e.contactCd <= 0) {
            e.contactCd = RAM_CD;
            shooter = seat;
            e.hurt(RAM, this);
        }
    }

    /** Splash: full damage inside the radius, half in the overlap fringe. The caller has
     *  already stamped {@code shooter} (the detonating round's owner) so kills pay right. */
    public void areaDamage(float x, float y, float radius, float damage) {
        for (Enemy e : enemies) {
            if (e.dead) continue;
            float dx = e.x - x, dy = e.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > radius + e.w / 2) continue;
            e.hurt(d <= radius ? damage : damage * 0.5f, this);
        }
    }

    /** Region-graded shop/checkpoint guards: a rank-1 leader on the post with two sentries
     *  flanking below it. Region 0 stays open — that is where the player spawns. */
    static final Enemy.Kind[][] GUARD = {
            { null, null },
            { Enemy.Kind.VAKRA, Enemy.Kind.AURIS },
            { Enemy.Kind.AURIS, Enemy.Kind.VAKRA },
            { Enemy.Kind.BHAVI, Enemy.Kind.VAKRA },
            { Enemy.Kind.BHAVI, Enemy.Kind.VAKRA },
            { Enemy.Kind.MAYAVI, Enemy.Kind.BHAVI },
    };

    void add(Enemy e) {
        if (clientSeat) return; // a guest's world holds no local roster; foes arrive as ghosts
        e.netId = enemySeq++; // the host's ledger: every foe a swarm can speak about
        // DIFFICULTY CURVE: the loka grades every unit it places, hull and fire alike, so a
        // deeper band's roster is meaner without any new behaviour code. An elite's doubling
        // rides on top of this, at placement.
        float hp = LokaTable.at(lokaIndex).hpScale;
        e.hp = e.maxHp = e.maxHp * hp;
        e.dispHp = e.maxHp;
        e.dmgScale = LokaTable.at(lokaIndex).dmgScale;
        for (TileMap m : tileMaps()) {
            int x0 = (int) Math.floor((e.homeX - e.w / 2 - 16 - m.ox) / TileMap.TILE);
            int x1 = (int) Math.floor((e.homeX + e.w / 2 + 16 - m.ox) / TileMap.TILE);
            int y0 = (int) Math.floor((e.homeY - e.h / 2 - 16 - m.oy) / TileMap.TILE);
            int y1 = (int) Math.floor((e.homeY + e.h / 2 + 16 - m.oy) / TileMap.TILE);
            for (int ty = y0; ty <= y1; ty++) {
                for (int tx = x0; tx <= x1; tx++) {
                    if (tx >= 0 && ty >= 0 && tx < m.cols && ty < m.rows) m.set(tx, ty, TileMap.EMPTY);
                }
            }
        }
        enemies.add(e);
    }

    public float time() {
        return clock;
    }

    // --- THE FORGED WEAPON (the Tatva Lance) -----------------------------------------------
    // Only the loka's OWN element feeds it (LOKA-LAWS §3), so the forge obeys the same purity
    // THE AKASHA ASTRA - Atala's forged weapon. Five shards forge it ONCE, and every five after
    // that buys one release. A release throws ASTRA_CUBES cubes out of the hull, each one picks
    // its own target and goes for it THROUGH ROCK, so the Astra reaches enemies a shot could
    // never see. The hull is mended whole and a life comes back while they fly.
    //
    // Expensive enough to be a run's turning point, cheap enough to plan around.
    public static final int ASTRA_CUBES = 20;       // cubes per release, one per target
    public static final float ASTRA_SPEED = 900f;    // px/s, and they steer, not fall
    public static final float ASTRA_TURN = 5.5f;     // rad/s of steering - fast enough to track
    public static final float ASTRA_DAMAGE = 90f;    // flat per cube: one-shots a regular unit
    public static final float FORGE_CD = 20f;        // seconds between releases

    /** THE CHARGE. F does not release - it winds up, and the release arrives on its own.
     *
     *  <p>This is the difference between a button and a moment. Fired instantly, twenty cubes is
     *  just a burst of numbers; held for nearly two seconds while the hull is heavy and slow,
     *  it is a decision you are living inside. The charge buys the release its weight: by the
     *  time the cage of twenty solids has tightened around the ship, the player has already
     *  committed, and everything after is release. */
    public static final float FORGE_WIND = 1.75f;
    /** The shell's radius at the start of the charge and at the instant before it lets go. */
    private static final float WIND_R_OUT = 150f, WIND_R_IN = 58f;
    /** Frames held still at the moment of release. Impact is mostly the absence of motion. */
    public static final float RELEASE_FREEZE = 0.14f;

    public float forgeCd;
    /** Seconds left of the charge; > 0 means the Astra is winding up and not yet released. */
    public float forgeWind;
    /** Frames to freeze the world on release. Drained by the screen, not here. */
    public float hitStop;
    /** 1 at the instant of release, decaying. The screen turns this into a pull BACK, so the
     *  Astra opens the view up rather than closing it in - the only way this camera moves. */
    public float astraPunch;

    // The release is drawn in the loka's own tatva, straight - not a fixed effect that gets
    // tinted. It used to be water blue with the element mixed in at 35%, which meant Atala's
    // violet Akasha came out a pale blue that was nobody's element. The crest is the body lifted
    // toward white by CREST_LIFT, which keeps the hue.
    private static final float CREST_LIFT = 0.55f;

    public float forgeCd() { return forgeCd; }
    public boolean winding() { return forgeWind > 0; }
    /** 0..1 through the charge. The renderer and the HUD both read this, so the cage, the shake
     *  and the corner gauge all wind up off one number. */
    public float windK() { return forgeWind <= 0 ? 1f : 1f - forgeWind / FORGE_WIND; }

    /** Ready to START a charge: a release in hand, no charge already running, and no cooldown.
     *  Without the middle term, mashing F would stack twenty cages and queue twenty releases. */
    public boolean forgeReady() {
        return forgeCd <= 0 && forgeWind <= 0 && hangar.hasForgeCharge(lokaIndex);
    }

    /** Cubes in flight or in the shell, oldest first. The renderer draws them; nothing else
     *  touches them. */
    public final List<AstraCube> astra = new ArrayList<>();

    /** START the charge. The release is spent and the cubes are out of the hull immediately -
     *  they just have nowhere to go until the wind finishes. False when there is no release in
     *  hand, or one is already winding. */
    public boolean useForge() {
        if (!forgeReady() || !hangar.spendForgeCharge(lokaIndex)) return false;
        forgeWind = FORGE_WIND;
        sound(Sfx.ASTRA_WIND); // the charge gathers
        ship.winding = true;
        ship.trauma = Math.max(ship.trauma, 0.35f); // the hull shudders as it loads

        float[] el = elementRgb();
        float cr = el[0] + (1f - el[0]) * CREST_LIFT;
        float cg = el[1] + (1f - el[1]) * CREST_LIFT;
        float cb = el[2] + (1f - el[2]) * CREST_LIFT;

        // THE CAGE GOES UP: twenty held cubes, evenly spaced on the shell, tumbling.
        for (int i = 0; i < ASTRA_CUBES; i++) {
            float slot = (float) (i * 2 * Math.PI / ASTRA_CUBES);
            AstraCube c = new AstraCube(ship.x + (float) Math.cos(slot) * WIND_R_OUT,
                    ship.y + (float) Math.sin(slot) * WIND_R_OUT, slot, 0f, null, ASTRA_DAMAGE);
            c.armed = false;
            c.slot = slot;
            c.spin = fxRng.nextFloat() * 6.2831853f;   // so no two start in phase
            c.hold(ship, 0f, 0f, WIND_R_OUT, WIND_R_IN);
            astra.add(c);
        }
        addEffect(Effect.ring(ship.x, ship.y, WIND_R_OUT, el[0], el[1], el[2]));
        popups.push(new Popups.Spec().text("THE AKASHA ASTRA").img("taptam")
                .at(ship.x, ship.y + 70).color(cr, cg, cb).scale(1.6f).life(FORGE_WIND + 0.6f));
        return true;
    }

    /** THE RELEASE. Called by {@link #updateAstra} the instant the charge runs out: every held
     *  cube gets its own target and goes, the hull is mended, a life comes back, and the screen
     *  is told to freeze and pull back so the moment lands. */
    private void release() {
        sound(Sfx.ASTRA_RELEASE);
        if (lives < MAX_LIVES) sound(Sfx.LIFE_UP); // the life that comes back
        forgeCd = FORGE_CD;
        ship.winding = false;
        ship.hp = ship.maxHp();               // the release knits the hull back together
        ship.shieldEnergy = ship.shieldCap();
        ship.iFrames = Math.max(ship.iFrames, 1.2f); // and buys the read to escape it
        ship.dipT = 0.35f;
        ship.trauma = Math.max(ship.trauma, 0.8f);  // and the whole screen takes it
        hitStop = RELEASE_FREEZE;
        astraPunch = 1f;

        float[] el = elementRgb();
        float cr = el[0] + (1f - el[0]) * CREST_LIFT;
        float cg = el[1] + (1f - el[1]) * CREST_LIFT;
        float cb = el[2] + (1f - el[2]) * CREST_LIFT;

        // One cube per living enemy, nearest first, each answerable to exactly one target. If
        // there are fewer than ASTRA_CUBES enemies the rest go untargeted rather than stacking
        // on the same body.
        Enemy[] targets = pickTargets(ASTRA_CUBES);
        for (int i = 0; i < astra.size() && i < ASTRA_CUBES; i++) {
            AstraCube c = astra.get(i);
            // fanned back from the nose, so the volley leaves as a volley
            float a = ship.facing + (float) Math.PI
                    + (i - (ASTRA_CUBES - 1) * 0.5f) * 0.16f;
            c.arm(a, ASTRA_SPEED, i < targets.length ? targets[i] : null);
        }
        // THE WIRE: a guest seat names the foes it aimed at and the hub arms the same volley
        // there — the Astra lands for real, and the kill pays the pilot who fired it.
        if (astraWire != null) {
            int[] ids = new int[ASTRA_CUBES];
            int n = 0;
            for (int i = 0; i < astra.size() && i < ASTRA_CUBES; i++) {
                Enemy t = astra.get(i).target;
                if (t != null) ids[n++] = t.netId;
            }
            astraWire.astra(ids, n);
        }
        // the tell at the hull: one ring in the element, and the crest breaking out of it
        // ONE ring, and a big one. The ring is a sprite scaled to its diameter with nearest
        // filtering: at ~9x (this radius) the art reads as the shockwave it was drawn to be, but
        // an 80 px ring at 4x is a fat donut pasted over the ship. The grandeur comes from
        // twenty cubes leaving at once, not from stacking circles.
        addEffect(Effect.ring(ship.x, ship.y, 170f, el[0], el[1], el[2]));
        for (int i = 0; i < ASTRA_CUBES; i++) // a spark off each cube as it lets go
            debris(ship.x, ship.y, 340, cr, cg, cb);

        if (lives < MAX_LIVES) lives++; // a life comes back, to the cap
    }

    /** The loka's own element, and the crest colour lifted toward white. Hoisted out of the
     *  release because the charge, the release and every cube impact all want the same pair. */
    public float[] elementRgb() { return TATVA_RGB[LokaTable.at(lokaIndex).shards[0]]; }

    /** The foe the host's ledger stamped with this id — the guest's Astra names its targets
     *  in the host's own numbering, so the hub can arm the same volley. */
    public Enemy enemyByNetId(int id) {
        for (Enemy e : enemies) if (e.netId == id && !e.dead && !e.ghost) return e;
        return null;
    }

    /** A guest's release, resolved on the host: the same volley, from that seat's hull, aimed
     *  at the same foes — and the kills pay the pilot who fired them. */
    public void astraFor(int seat, int[] ids) {
        Ship from = ship;
        if (seat != 0) {
            if (seat == partnerSeat && partner != null) {
                from = partner;
            } else {
                for (SeatHull gh : extras)
                    if (gh.seat == seat && gh.ship != null) { from = gh.ship; break; }
            }
        }
        for (int i = 0; i < ids.length; i++) {
            Enemy t = enemyByNetId(ids[i]);
            if (t == null) continue;
            AstraCube c = new AstraCube(from.x, from.y, 0f, 0f, t, ASTRA_DAMAGE);
            c.arm(from.facing + (float) Math.PI
                    + (i - (ASTRA_CUBES - 1) * 0.5f) * 0.16f, ASTRA_SPEED, t);
            c.owner = seat; // the kill is theirs
            astra.add(c);
        }
    }

    public float crestR() { float[] e = elementRgb(); return e[0] + (1f - e[0]) * CREST_LIFT; }
    public float crestG() { float[] e = elementRgb(); return e[1] + (1f - e[1]) * CREST_LIFT; }
    public float crestB() { float[] e = elementRgb(); return e[2] + (1f - e[2]) * CREST_LIFT; }

    /** The ASTRA_CUBES nearest living enemies, nearest first. One pass of insertion into a
     *  caller-sized array: the roster is hundreds long and this runs once per release, so the
     *  simple O(n*k) selection is cheaper than any sort. */
    private Enemy[] pickTargets(int want) {
        Enemy[] best = new Enemy[Math.min(want, enemies.size())];
        int filled = 0;
        for (Enemy e : enemies) {
            if (e.dead || e.dismissed) continue;
            float d = (e.x - ship.x) * (e.x - ship.x) + (e.y - ship.y) * (e.y - ship.y);
            int at = filled;
            while (at > 0 && dist2(best[at - 1], ship) > d) {
                if (at < best.length) best[at] = best[at - 1];
                at--;
            }
            if (at < best.length) best[at] = e;
            if (filled < best.length) filled++;
        }
        // trim any unfilled tail
        for (int i = 0; i < best.length; i++) if (best[i] == null) {
            int j = i;
            while (j < best.length && best[j] == null) j++;
            if (j < best.length) System.arraycopy(best, j, best, i, best.length - j);
            break;
        }
        return best;
    }

    private static float dist2(Enemy e, vajra.ship.Ship s) {
        if (e == null) return Float.MAX_VALUE;
        return (e.x - s.x) * (e.x - s.x) + (e.y - s.y) * (e.y - s.y);
    }

    /** Cubes wind up, then advance and retire. Rock is never consulted - that is the whole
     *  point of them. */
    public void updateAstra(float dt) {
        if (forgeWind > 0) {
            forgeWind -= dt;
            float k = windK();
            for (AstraCube c : astra) c.hold(ship, dt, k, WIND_R_OUT, WIND_R_IN);
            // one spark off the cage per frame, so the charge is never a static picture
            if (fxRng.nextFloat() < 0.6f) {
                AstraCube c = astra.get(fxRng.nextInt(astra.size()));
                debris(c.x, c.y, 120, crestR(), crestG(), crestB());
            }
            if (forgeWind <= 0) release();
            return;                       // the cage does not also start homing this frame
        }
        astraPunch *= (float) Math.exp(-4.5f * dt);

        for (int i = astra.size() - 1; i >= 0; i--) {
            AstraCube c = astra.get(i);
            boolean hit = c.think(this, dt, ASTRA_TURN);
            if (hit || c.life <= 0 || c.x < -200 || c.x > width + 200
                    || c.y < -200 || c.y > height + 200) {
                if (hit) addEffect(Effect.ring(c.x, c.y, 26, crestR(), crestG(), crestB()));
                astra.remove(i);
            }
        }
    }

    /** Random point in the built regions that is not inside a wall. Falls back to Bhu Loka. */
    public float[] randomOpenSpot(Random rng) {
        for (int i = 0; i < 8; i++) {
            float x = 100 + rng.nextFloat() * (width - 200);
            float y = 100 + rng.nextFloat() * (height - 200);
            if (!blockedPx(x - 16, y - 16) && !blockedPx(x + 16, y - 16)
                    && !blockedPx(x - 16, y + 16) && !blockedPx(x + 16, y + 16)) {
                return new float[] { x, y };
            }
        }
        return new float[] { width / 2, 800 };
    }

    public boolean blockedPx(float x, float y) {
        for (TileMap m : maps) if (m.solidPx(x, y)) return true;
        return false;
    }

    /** Pushes the rect out of every map and every shop wall. Returns corrected x,y.
     *  Writes into the instance scratch buffer; callers read it immediately. */
    private final float[] collideBuf = new float[2];

    public float[] collide(float x, float y, float w, float h) {
        for (TileMap m : maps) {
            float[] c = m.pushOut(x, y, w, h);
            x = c[0];
            y = c[1];
        }
        collideBuf[0] = x;
        collideBuf[1] = y;
        for (float[] shop : shops) // a shop's board and legs are solid: no flying through them
            for (float[] wall : DOCK_WALLS)
                pushOutRect(collideBuf, w, h, shop[0] + wall[0], shop[1] + wall[1], wall[2], wall[3]);
        return collideBuf;
    }

    // Solid parts of a shop: x, y, w, h as world offsets from the sign centre. The posts' inner
    // faces ARE the square's sides; the floor stays open between the feet — you rise up into it.
    private static final float[][] DOCK_WALLS = {
            { -56, -16, 112, 72 }, // the sign board
            { -52, -56, 16, 40 },  // left leg
            { 40, -56, 16, 40 },   // right leg
            { -26, -52, 10, 32 },  // left dock post (art cols 10-11)
            { 20, -52, 8, 32 },    // right dock post (art cols 21-22)
    };

    /** AABB push-out along the smallest penetration — what TileMap.pushOut does for tiles.
     *  xy is the rect's bottom-left corner in, corrected corner out; no allocation. */
    private static void pushOutRect(float[] xy, float w, float h, float rx, float ry, float rw, float rh) {
        float l = xy[0] + w - rx, r = rx + rw - xy[0], b = xy[1] + h - ry, t = ry + rh - xy[1];
        if (l <= 0 || r <= 0 || b <= 0 || t <= 0) return; // no overlap
        float m = Math.min(Math.min(l, r), Math.min(b, t));
        if (m == l) xy[0] -= l;
        else if (m == r) xy[0] += r;
        else if (m == b) xy[1] -= b;
        else xy[1] += t;
    }

    /** Cached: this is called per entity per frame, it must not allocate. */
    public TileMap[] tileMaps() {
        return maps;
    }

    TileMap[] maps;

    public void spawnBullet(float x, float y, float angle, WeaponDef def, float power, float dmgMult) {
        if (bullets.size() >= 400) return;
        Bullet b = bulletPool.obtain();
        b.init(x, y, angle, def, power, dmgMult, false);
        bullets.add(b);
        sound(Sfx.forWeapon(def, power), x, y); // the report: one per volley, the player rate-limits
        addEffect(Effect.flash(x, y, x + b.vx * 0.02f, y + b.vy * 0.02f, b.r, b.g, b.b)); // muzzle flash
        if (shotWire != null) shotWire.shot(shotOf(b)); // my round, announced so both skies match
    }

    /** Enemy fire. Held under 300 so the player always keeps slots for their own gun. */
    public void spawnEnemyBullet(float x, float y, float angle) {
        spawnEnemyBullet(x, y, angle, Bullet.HOSTILE, 1f);
    }

    /** Ungraded hostile fire (Bala runs his own rows at the loka's scale). */
    public void spawnEnemyBullet(float x, float y, float angle, WeaponDef def) {
        spawnEnemyBullet(x, y, angle, def, 1f);
    }

    /** Same, with the boss's own shot rows (speed/damage/colour differ per attack).
     *  dmgScale is the loka's fire multiplier — a deeper band bites harder. */
    public void spawnEnemyBullet(float x, float y, float angle, WeaponDef def, float dmgScale) {
        if (bullets.size() >= 300) return;
        Bullet b = bulletPool.obtain();
        b.init(x, y, angle, def, 1f, dmgScale, true);
        bullets.add(b);
        sound(Sfx.ENEMY_SHOT, x, y); // a foe fires: quieter with distance, placed by where it is
        addEffect(Effect.flash(x, y, x + b.vx * 0.04f, y + b.vy * 0.04f, b.r, b.g, b.b)); // muzzle flash
        if (shotWire != null) shotWire.shot(shotOf(b)); // hostile fire streams to the guest too
    }

    /** The wire's read of a round: pose, bite, tint — no def key, the fallback draws it. */
    private Shot shotOf(Bullet b) {
        Shot s = new Shot();
        s.x = b.x;
        s.y = b.y;
        s.vx = b.vx;
        s.vy = b.vy;
        s.damage = b.damage;
        s.radius = b.radius;
        s.r = b.r;
        s.g = b.g;
        s.b = b.b;
        s.hostile = b.hostile;
        return s;
    }

    /** A pooled bullet for the wire's inbound rounds (the pool's house rule: construct via World). */
    public Bullet obtainBullet() {
        return bulletPool.obtain();
    }

    /** The seal: every kind's kill quota filled — only then does Bala notice the ship. */
    public boolean quotaMet() {
        for (int i = 0; i < quota.length; i++) if (kills[i] < quota[i]) return false;
        return true;
    }

    /** The seal lifts: Bala appears in his arena the moment the run's kill quota is full.
     *  Nothing on screen counts down — the loka simply changes. */
    public void trySpawnBoss() {
        enemyDirector.trySpawnBoss();
    }

    public void countKill(Enemy e) {
        kills[e.kind.ordinal()]++;
        if (shooter != 0) { // a partner's kill: the seals stay shared, the pay flies home
            if (painWire != null) {
                Pain p = new Pain();
                p.target = shooter;
                p.kind = PAIN_KILL;
                p.a = e.kind.ordinal() | (e.boss ? 0x100 : 0) | (e.elite ? 0x200 : 0)
                        | (e.rank == 1 ? 0x400 : 0);
                p.b = e.x;
                p.c = e.y;
                painWire.pain(p);
            }
            return;
        }
        long before = score.points;
        int mult = score.mult();
        score.kill(this, e);
        long paid = score.points - before; // kill value + any leader/wipe flat, multiplier in
        if (paid > 0)
            popups.push(new Popups.Spec().text("+" + paid).at(e.x, e.y + 10)
                    .color(1f, 215 / 255f, 0).scale(e.boss || e.elite ? 1.4f : 1f));
        if (score.mult() > mult)
            popups.push(new Popups.Spec().text(tierWord(score.mult())).img(tierArt(score.mult()))
                    .at(ship.x, ship.y + 28).color(1f, 215 / 255f, 0).scale(1.5f).life(1.6f));
    }

    /** The scripture pair for a multiplier tier: the pre-rendered Devanagari word and its
     *  English line. Package-private: the headless test pins the mapping. */
    static String tierArt(int mult) {
        switch (mult) {
            case 2: return "dvitiya";
            case 3: return "tritiya";
            case 4: return "chaturth";
            default: return "pancham"; // the ladder caps at five
        }
    }

    static String tierWord(int mult) {
        switch (mult) {
            case 2: return "TWOFOLD";
            case 3: return "THREEFOLD";
            case 4: return "FOURFOLD";
            default: return "FIVEFOLD";
        }
    }

    /** The call: the whole arena retinue leaves its post and forms the commander's ring. */
    public void orderRetinue(Enemy commander) {
        enemyDirector.orderRetinue(commander);
    }

    /** Bala falls: the retinue stands down, the ring effect goes up, the purse changes hands,
     *  and the gold gate across the north corridor opens to the next loka. */
    public void victory(Enemy b) {
        sound(Sfx.BOSS_DEATH);
        sound(Sfx.VICTORY); // the fanfare opens after a beat of silence of its own
        bossDead = true;
        won = true;
        victoryT = 0;
        score.add(Score.BOSS);
        popups.push(new Popups.Spec().text("BALA SLAIN").img("bala").at(b.x, b.y + 40)
                .color(1f, 215 / 255f, 0).scale(1.8f).life(2.2f));
        for (Enemy e : enemies)
            if (!e.dead && e.commander == b) {
                e.dismissed = true;
                e.commander = null;
            }
        hangar.coins += 150;
        for (int c = 72; c <= 88; c++) tileMapBoss.set(c, 93, TileMap.EMPTY);
        addEffect(Effect.ring(WORLD_CX, 20560, 260, 1f, 0.84f, 0.3f));
        addEffect(Effect.ring(b.x, b.y, 500, 1f, 0.84f, 0.3f));
        addEffect(Effect.ring(b.x, b.y, 260, 1f, 1f, 1f));
        ship.trauma = Math.min(1f, ship.trauma + 0.5f);
    }

    /** Mid-berserk deployment: a gold elite pair at his old post, straight into his ring (cap 6). */
    public void summonElites(float x, float y, Enemy commander) {
        enemyDirector.summonElites(x, y, commander);
    }

    /** A random open berth in the arena for the boss's blink: 350-850 px from the ship. */
    public float[] bossBlinkSpot() {
        return enemyDirector.bossBlinkSpot();
    }

    /** Beam: instant line from (x,y) along angle, stops at the first wall or at range, and scalds what it crosses. */
    public void laser(float x, float y, float angle, float range, float r, float g, float b, float damage) {
        sound(Sfx.SHOT_LASER, x, y);
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        float d = wallDist(x, y, dx, dy, range);
        float ex = x + dx * d, ey = y + dy * d;
        for (Enemy e : enemies) {
            if (!e.dead && segDist(e.x, e.y, x, y, ex, ey) <= e.w / 2 + 6) e.hurt(damage, this);
        }
        addEffect(Effect.flash(x, y, ex, ey, r, g, b));
        beamEcho(x, y, angle, damage, r, g, b); // co-op: a flyer stands in for the line elsewhere
    }

    /** A beam's wire echo: a fast flyer is the beam on every OTHER screen — the real hit
     *  stays the firing seat's word, so the flyer is visuals, never a second verdict.
     *  No local round is created: the firing machine announces, nobody resolves twice. */
    private void beamEcho(float x, float y, float angle, float damage, float r, float g, float b) {
        if (shotWire == null) return;
        Shot s = new Shot();
        s.x = x;
        s.y = y;
        s.vx = (float) Math.cos(angle) * 3000f;
        s.vy = (float) Math.sin(angle) * 3000f;
        s.damage = damage;
        s.radius = 7;
        s.r = r;
        s.g = g;
        s.b = b;
        s.hostile = false;
        shotWire.shot(s);
    }

    /** Distance along a unit ray until rock, sampled every 16px; range when nothing is in the way. */
    public float wallDist(float x, float y, float ux, float uy, float range) {
        for (float d = 16; d <= range; d += 16) {
            if (blockedPx(x + ux * d, y + uy * d)) return d;
        }
        return range;
    }

    /** True when no rock sits between two points. Enemies ask before they open fire. */
    public boolean clearLine(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return true;
        return wallDist(ax, ay, dx / len, dy / len, len) >= len;
    }

    private static float segDist(float px, float py, float ax, float ay, float bx, float by) {
        float vx = bx - ax, vy = by - ay;
        float len2 = vx * vx + vy * vy;
        float t = len2 < 0.01f ? 0 : ((px - ax) * vx + (py - ay) * vy) / len2;
        t = Math.max(0, Math.min(1, t));
        float cx = ax + vx * t - px, cy = ay + vy * t - py;
        return (float) Math.sqrt(cx * cx + cy * cy);
    }

    /** Snaps aim to the nearest enemy inside the cone. Returns aim unchanged when cone <= 0. */
    public float assistAim(float aim, float coneDeg) {
        if (coneDeg <= 0) return aim;
        float best = aim, bestDiff = (float) Math.toRadians(coneDeg);
        for (Enemy e : enemies) {
            if (e.dead) continue;
            float dx = e.x - ship.x, dy = e.y - ship.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > 900) continue;
            float to = (float) Math.atan2(dy, dx);
            float diff = Math.abs(wrapAngle(to - aim));
            if (diff < bestDiff) {
                bestDiff = diff;
                best = to;
            }
        }
        return best;
    }

    /** Shortest signed delta between two angles, in (-pi, pi]. */
    public static float wrapAngle(float a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    public void addEffect(Effect e) {
        effects.add(e);
    }

    /** One debris spark flying outward from (x, y). */
    public void debris(float x, float y, float speed, float r, float g, float b) {
        float a = fxRng.nextFloat() * 6.2831853f;
        float sp = speed * (0.4f + fxRng.nextFloat());
        addEffect(Effect.spark(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                2.5f, 0.5f, 3, r, g, b));
    }

    /** Three sparks thrown along a shot's path from where it landed. */
    public void spray(float x, float y, float ux, float uy, float r, float g, float b) {
        float ang = (float) Math.atan2(uy, ux);
        for (int k = -1; k <= 1; k++) {
            float a = ang + k * 0.45f;
            float sp = 70 + (k + 1) * 35;
            addEffect(Effect.spark(x, y, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                    3, 0.35f, 3, r, g, b));
        }
    }

    public void selectShip(int i) {
        if (i < 0 || i >= ShipDef.ALL.length || !hangar.ownsShip(i)) return;
        hangar.shipIndex = i;
        ship.setDef(ShipDef.ALL[i]);
    }

    public void selectWeapon(int i) {
        if (i < 0 || i >= WeaponDef.ALL.length) return;
        hangar.weaponIndex = i;
        ship.weapon.def = WeaponDef.ALL[i];
    }

    public int currentRegionIndex() {
        float cy = ship.y;
        for (int i = 0; i < regionNames.length; i++) {
            if (cy >= regionStart[i] && cy < regionEnd[i]) return i;
        }
        return regionNames.length - 1;
    }

    public String currentRegion() {
        return regionNames[currentRegionIndex()];
    }

    /** Which loka the ship is in: Bhu before the gate, Atala beyond it. */
    public String lokaName() {
        return currentRegionIndex() == 0 ? "Bhu" : "Atala";
    }

}