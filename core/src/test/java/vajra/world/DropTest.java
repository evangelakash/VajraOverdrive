package vajra.world;

import vajra.enemy.Enemy;
import vajra.ship.ShipControls;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Loot: the table a death pays, where the coins land, and the hull sweeping them up. */
class DropTest {
    private static final ShipControls NONE = new ShipControls() {
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

    private static World empty() {
        World w = new World();
        w.enemies.clear();
        w.pickups.clear();
        w.lootRng = new Random(1); // pinned: the health roll must never shift the coin counts
        return w;
    }

    private static int coins(World w) {
        int n = 0;
        for (Pickup p : w.pickups) if (p.type == Pickup.COIN) n++;
        return n;
    }

    @Test
    void higherRanksPayMore() {
        World w = empty();
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 2600, 800);
        w.dropLoot(e);
        assertEquals(2, coins(w), "rank 0 fodder");
        w.pickups.clear();
        e.rank = 1;
        w.dropLoot(e);
        assertEquals(4, coins(w), "commander and posted guards");
        w.pickups.clear();
        e.elite = true;
        w.dropLoot(e);
        assertEquals(6, coins(w), "an elite doubles down");
        w.pickups.clear();
        e.boss = true;
        w.dropLoot(e);
        assertEquals(25, coins(w), "Bala's purse");
    }

    @Test
    void coinsLandInsideTheCorpseRadiusThenFloat() {
        World w = empty();
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 2600, 800); // w = 48
        w.dropLoot(e);
        assertTrue(w.pickups.size() >= 2, "at least the two coins");
        for (int i = 0; i < 150; i++) for (Pickup p : w.pickups) p.update(1 / 60f); // 2.5s: settles
        for (Pickup p : w.pickups) {
            float x = p.x, y = p.y;
            double d = Math.hypot(x - e.x, y - e.y);
            assertTrue(d <= e.w + 2, "settled inside the body-wide ring: " + d);
            assertEquals(0f, p.vx, 0.01f, "drift dies out");
            p.update(1 / 60f);
            assertEquals(x, p.x, 0.001f, "no gravity: it floats, it does not sink");
            assertEquals(y, p.y, 0.001f, "no gravity: it floats, it does not sink");
        }
    }

    @Test
    void oneDeathFansEveryDropAroundTheRing() {
        World w = empty();
        w.lootRng = rng(0f); // every roll passes: coins + health + shard, all at the inner edge
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, 2600, 800);
        w.dropLoot(e);
        assertEquals(4, w.pickups.size(), "2 coins, a mend pop and a shard — one burst");
        for (int i = 0; i < 150; i++) for (Pickup p : w.pickups) p.update(1 / 60f);
        float[] ang = new float[w.pickups.size()];
        for (int i = 0; i < ang.length; i++) {
            Pickup p = w.pickups.get(i);
            ang[i] = (float) Math.atan2(p.y - e.y, p.x - e.x);
        }
        Arrays.sort(ang);
        for (int i = 0; i < ang.length; i++) { // even slices: four drops, four quarter arcs
            float gap = (i + 1 < ang.length ? ang[i + 1] : ang[0] + 6.2831853f) - ang[i];
            assertTrue(gap > 1f && gap < 2.1f, "a quarter-arc slice, got a " + gap + " rad gap");
        }
    }

    @Test
    void hullSweepsLootIntoThePurse() {
        World w = empty();
        int coins0 = w.hangar.coins;
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, w.ship.x, w.ship.y); // corpse under the hull
        e.rank = 1;
        assertTrue(e.hurt(9999, w), "the kill lands");
        assertTrue(e.dead, "and it pays on the death path");
        assertEquals(4, coins(w), "rank 1 drop, whatever health rolled");
        w.ship.hp = w.ship.maxHp() * 0.5f; // damaged: a rolled health drop sweeps up with the coins
        w.update(1 / 60f, NONE); // one frame: the pops start on the hull, so the sweep takes them
        assertEquals(coins0 + 4, w.hangar.coins, "the purse took the ring");
        assertTrue(w.pickups.isEmpty(), "nothing left to sweep");
    }

    @Test
    void healthRepairsQuarterOfHullAndWaitsWhenFull() {
        World w = empty();
        Pickup drop = new Pickup();
        drop.type = Pickup.HEALTH;
        drop.x = w.ship.x;
        drop.y = w.ship.y;
        w.pickups.add(drop);

        w.ship.hp = w.ship.maxHp() * 0.5f;
        w.update(1 / 60f, NONE);
        assertEquals(w.ship.maxHp() * 0.75f, w.ship.hp, 0.5f, "heals a quarter of the hull");
        assertTrue(w.pickups.isEmpty());

        w.ship.hp = w.ship.maxHp() - 10; // close enough: never overheal
        Pickup near = new Pickup();
        near.type = Pickup.HEALTH;
        near.x = w.ship.x;
        near.y = w.ship.y;
        w.pickups.add(near);
        w.update(1 / 60f, NONE);
        assertEquals(w.ship.maxHp(), w.ship.hp, 0.5f, "capped at the hull maximum");

        Pickup late = new Pickup(); // full hull: it keeps floating until damage lands
        late.type = Pickup.HEALTH;
        late.x = w.ship.x;
        late.y = w.ship.y;
        w.pickups.add(late);
        w.update(1 / 60f, NONE);
        assertEquals(1, w.pickups.size(), "a full hull leaves the drop floating");
    }

    /** A stub that pins every roll: 0 makes every chance pass, 0.99 fails them all. */
    private static Random rng(float v) {
        return new Random() {
            @Override
            public float nextFloat() {
                return v;
            }
        };
    }

    @Test
    void shardRollsKeyedOffTheLokaAndTheArchitectDropsThePiece() {
        World w = empty();
        w.lootRng = rng(0f); // every roll passes
        Enemy e = new Enemy(Enemy.Kind.MAYAVI, w.ship.x, w.ship.y);
        w.dropLoot(e);
        int shards = 0;
        for (Pickup p : w.pickups) if (p.type == Pickup.SHARD) {
            shards++;
            assertEquals(0, p.variant, "Atala pays Akasha (LOKA-LAWS: 1 tatva per early loka)");
        }
        assertEquals(1, shards, "one shard when the roll passes");
        assertEquals(2, coins(w), "and the coins still land");
        w.update(1 / 60f, NONE); // spawned on the hull: swept immediately
        assertEquals(1, w.hangar.shards[0], "the stock takes it");

        w.pickups.clear();
        w.lootRng = rng(0.99f); // every roll fails
        w.dropLoot(e);
        assertEquals(2, coins(w), "a dry roll pays only coins");
        assertEquals(2, w.pickups.size(), "no health, no shard");

        w.pickups.clear();
        w.lootRng = rng(0.99f); // even a dry roll cannot hold back the piece
        Enemy b = new Enemy(Enemy.Kind.BALA, w.ship.x, w.ship.y);
        b.boss = true;
        w.dropLoot(b);
        int pieces = 0;
        for (Pickup p : w.pickups) if (p.type == Pickup.PIECE) {
            pieces++;
            assertEquals(0, p.variant, "variant = loka index: Atala's piece");
        }
        assertEquals(1, pieces, "the architect always drops his loka's Vajrayudha piece");
        w.update(1 / 60f, NONE); // health waits (full hull), the piece sweeps up
        assertTrue(w.hangar.pieces[0], "recorded as recovered");
    }

    @Test
    void magnetTugsLootAtFifteen() {
        World w = empty();
        assertEquals(15f, w.hangar.magnet, 0.0001f, "fixed at 15 for dev builds");

        Pickup near = new Pickup(); // inside reach: the fixed 15 tug drags it home fast
        near.type = Pickup.COIN;
        near.x = w.ship.x + 60;
        near.y = w.ship.y;
        w.pickups.add(near);
        int coinsBefore = w.hangar.coins;
        for (int i = 0; i < 120; i++) w.update(1 / 60f, NONE); // 2s
        assertTrue(w.hangar.coins > coinsBefore, "the tug pulled the coin in and it was collected");

        Pickup far = new Pickup(); // beyond PULL_R: left alone
        far.type = Pickup.COIN;
        far.x = w.ship.x + World.PULL_R + 100;
        far.y = w.ship.y;
        w.pickups.add(far);
        float fx = far.x;
        for (int i = 0; i < 120; i++) w.update(1 / 60f, NONE);
        assertEquals(fx, far.x, 0.001f, "out of reach: no tug at all");
        w.pickups.remove(far);

        w.hangar.magnet = 1f; // full power: gathers in a heartbeat
        Pickup strong = new Pickup();
        strong.type = Pickup.COIN;
        strong.x = w.ship.x + 60;
        strong.y = w.ship.y;
        w.pickups.add(strong);
        for (int i = 0; i < 30; i++) w.update(1 / 60f, NONE); // 0.5s
        assertTrue(strong.x < w.ship.x + 30, "100% hauls it in fast: " + (strong.x - w.ship.x) + "px");
    }

    @Test
    void vajraFormsBandByBandAndTheSeventhEndsTheRun() {
        World w = empty();
        for (int i = 1; i < 7; i++) w.hangar.pieces[i] = true; // six bands already home
        Pickup band = new Pickup();
        band.type = Pickup.PIECE;
        band.variant = 0; // Atala's band
        band.x = w.ship.x;
        band.y = w.ship.y;
        w.pickups.add(band);
        w.update(1 / 60f, NONE);
        assertTrue(w.hangar.pieces[0], "the band is recovered");
        assertTrue(w.vajraDone, "the seventh band closes the blade: the run is won");

        World fresh = empty();
        Pickup one = new Pickup();
        one.type = Pickup.PIECE;
        one.variant = 0;
        one.x = fresh.ship.x;
        one.y = fresh.ship.y;
        fresh.pickups.add(one);
        fresh.update(1 / 60f, NONE);
        assertFalse(fresh.vajraDone, "one band is a start, not a blade");
    }

    /** Nothing sits in the world forever: a band you cleared and walked away from empties
     *  itself, instead of leaving permanent coins and a full-hull health pop adrift. */
    @Test
    void uncollectedLootFadesOutOnItsOwn() {
        World w = empty();
        Pickup coin = new Pickup();
        coin.type = Pickup.COIN;
        coin.x = w.ship.x + 900; // well out of magnet and collect range
        coin.y = w.ship.y;
        w.pickups.add(coin);
        assertEquals(1, w.pickups.size());

        w.update(Pickup.LIFE - Pickup.FADE - 1f, NONE);
        assertEquals(1, w.pickups.size(), "still there with time left on it");

        w.update(Pickup.FADE + 2f, NONE); // through the tail and past LIFE
        assertEquals(0, w.pickups.size(), "and gone when its life runs out");
        assertEquals(0, w.hangar.coins, "a faded pop pays nothing");
    }

    @Test
    void aPickupFadesOnlyOverItsLastFewSeconds() {
        Pickup p = new Pickup();
        assertEquals(1f, p.alpha(), 0.001f, "fresh pops draw solid");
        p.life = Pickup.FADE * 2;
        assertEquals(1f, p.alpha(), 0.001f);
        p.life = Pickup.FADE / 2;
        assertEquals(0.5f, p.alpha(), 0.001f, "and dim through the tail");
        p.life = 0;
        assertEquals(0f, p.alpha(), 0.001f);
    }
}
