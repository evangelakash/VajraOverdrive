package vajra.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vajra.enemy.Enemy;
import vajra.ship.ShipControls;
import vajra.weapon.WeaponDef;
import vajra.world.LootSystem;
import vajra.world.Pickup;
import vajra.world.World;

/** THE RIGHT SOUND FOR THE RIGHT EVENT. A recording bus stands in for the sound card, so these
 *  assert which sound each thing in the game makes, how loud it is for how far away it is, and
 *  that nothing a player could not hear is played. */
class SfxEventsTest {
    private static final class Rec implements SfxBus {
        final List<Sfx> played = new ArrayList<>();
        final List<Float> vols = new ArrayList<>();
        final List<Float> pans = new ArrayList<>();
        float engine = -1;
        boolean boost;
        int stops;

        @Override public void play(Sfx s, float v, float p) { played.add(s); vols.add(v); pans.add(p); }
        @Override public void engine(float level, boolean b) { engine = level; boost = b; }
        @Override public void stopLoops() { stops++; }
        boolean heard(Sfx s) { return played.contains(s); }
        float volOf(Sfx s) { return vols.get(played.indexOf(s)); }
        float panOf(Sfx s) { return pans.get(played.indexOf(s)); }
    }

    private Rec rec;

    @BeforeEach
    void listen() {
        rec = new Rec();
        Audio.use(rec);
    }

    @AfterEach
    void hush() {
        Audio.use(null);
    }

    private static World world() {
        World w = new World();
        w.enemies.clear();
        w.pickups.clear();
        return w;
    }

    private static final class Pad implements ShipControls {
        boolean dash, boost, shield;
        float tx, ty;
        public float thrustX() { return tx; }
        public float thrustY() { return ty; }
        public float rotate() { return 0; }
        public boolean fire() { return false; }
        public boolean boost() { return boost; }
        public boolean dash() { return dash; }
        public boolean shield() { return shield; }
        public boolean mouseAim() { return false; }
        public float aimX() { return 0; }
        public float aimY() { return 0; }
    }

    // ---- the guns -----------------------------------------------------------------------------
    @Test
    void everyWeaponHasItsOwnKindOfReport() {
        assertEquals(Sfx.SHOT_MAGNUM, Sfx.forWeapon(WeaponDef.SINGLE, 1f));
        assertEquals(Sfx.SHOT_MAGNUM, Sfx.forWeapon(WeaponDef.DOUBLE, 1f));
        assertEquals(Sfx.SHOT_SNIPER, Sfx.forWeapon(WeaponDef.PIERCE, 1f));
        assertEquals(Sfx.SHOT_RIFLE, Sfx.forWeapon(WeaponDef.BURST1, 1f));
        assertEquals(Sfx.SHOT_MG, Sfx.forWeapon(WeaponDef.LIGHT, 1f));
        assertEquals(Sfx.SHOT_SHELL, Sfx.forWeapon(WeaponDef.HEAVY, 1f));
        assertEquals(Sfx.SHOT_SCATTER, Sfx.forWeapon(WeaponDef.SPREAD, 1f));
        assertEquals(Sfx.SHOT_LASER, Sfx.forWeapon(WeaponDef.LASER, 1f));
        assertEquals(Sfx.SHOT_MISSILE, Sfx.forWeapon(WeaponDef.MISSILE, 1f));
        assertEquals(Sfx.SHOT_MISSILE, Sfx.forWeapon(WeaponDef.TRACKING, 1f));
        assertEquals(Sfx.SHOT_BOMB, Sfx.forWeapon(WeaponDef.BOMB, 1f));
        assertEquals(Sfx.SHOT_CHARGED, Sfx.forWeapon(WeaponDef.CHARGE, 2f));
        for (WeaponDef d : WeaponDef.ALL) assertNotNull(Sfx.forWeapon(d, 1f), d.name);
    }

    @Test
    void firingMakesTheWeaponsReportAtFullVolumeAtTheMuzzle() {
        World w = world();
        w.spawnBullet(w.ship.x, w.ship.y, 0f, WeaponDef.HEAVY, 1f, 1f);
        assertTrue(rec.heard(Sfx.SHOT_SHELL));
        assertEquals(1f, rec.volOf(Sfx.SHOT_SHELL), 0.001f, "your own gun is at full volume");
        assertEquals(0f, rec.panOf(Sfx.SHOT_SHELL), 0.001f, "and dead centre");
    }

    @Test
    void aFoesShotIsQuieterWithDistanceAndPlacedBySide() {
        World w = world();
        w.spawnEnemyBullet(w.ship.x + 500, w.ship.y, 0f);
        assertTrue(rec.heard(Sfx.ENEMY_SHOT), "a foe in earshot is heard");
        assertTrue(rec.volOf(Sfx.ENEMY_SHOT) < 1f, "but quieter than your own gun");
        assertTrue(rec.panOf(Sfx.ENEMY_SHOT) > 0.2f, "on the right, as it is");

        Rec far = new Rec();
        Audio.use(far);
        w.spawnEnemyBullet(w.ship.x - 3000, w.ship.y, 0f);
        assertTrue(far.played.isEmpty(), "a shot three screens away makes no sound");
    }

    @Test
    void theFalloffIsMonotonicAndEndsAtEarshot() {
        float prev = 2f;
        for (float d = 0; d <= Audio.EARSHOT; d += 50) {
            float v = Audio.volumeAt(d, 0);
            assertTrue(v <= prev + 1e-6f, "never louder further away");
            prev = v;
        }
        assertEquals(1f, Audio.volumeAt(0, 0), 0.001f);
        assertEquals(0f, Audio.volumeAt(Audio.EARSHOT, 0), 0.001f);
        assertEquals(0f, Audio.volumeAt(5000, 5000), 0.001f);
        assertTrue(Audio.panAt(-400) < 0 && Audio.panAt(400) > 0 && Audio.panAt(0) == 0);
        assertTrue(Math.abs(Audio.panAt(9999)) <= 0.76f, "never a hard single-ear click");
    }

    // ---- the fight ----------------------------------------------------------------------------
    @Test
    void aHitThenAKillThenTheDrops() {
        World w = world();
        Enemy e = new Enemy(Enemy.Kind.VAKRA, w.ship.x, w.ship.y + 200);
        w.enemies.add(e);
        e.hurt(1f, w);
        assertTrue(rec.heard(Sfx.HIT_ENEMY), "a round that lands ticks");
        e.hurt(99999f, w);
        assertTrue(rec.heard(Sfx.EXPLODE_S) || rec.heard(Sfx.EXPLODE_M), "the foe falls with a bang");
        assertTrue(rec.heard(Sfx.DROP), "and its coins leave the body");
    }

    @Test
    void biggerBodiesFallLouder() {
        World w = world();
        Enemy big = new Enemy(Enemy.Kind.BALA, w.ship.x, w.ship.y + 200);
        big.boss = true;
        w.enemies.add(big);
        big.hurt(999999f, w);
        assertTrue(rec.heard(Sfx.EXPLODE_L), "a boss body is the large explosion");
        assertTrue(rec.heard(Sfx.BOSS_DEATH) && rec.heard(Sfx.VICTORY), "and Bala's fall is the fanfare");
    }

    // ---- the ship -----------------------------------------------------------------------------
    @Test
    void theShieldAndTheHullAnswerDifferently() {
        World w = world();
        w.ship.shieldOn = true;
        w.ship.shieldEnergy = 50;
        w.ship.damage(5, w);
        assertTrue(rec.heard(Sfx.SHIELD_HIT));
        assertFalse(rec.heard(Sfx.HULL_HIT), "a shielded hit does not hurt the hull");

        World w2 = world();
        rec.played.clear();
        w2.ship.damage(5, w2);
        assertTrue(rec.heard(Sfx.HULL_HIT));
    }

    @Test
    void losingALifeAndLosingTheRun() {
        World w = world();
        w.lives = 3;
        w.ship.damage(9999, w);
        assertTrue(rec.heard(Sfx.SHIP_DESTROYED) && rec.heard(Sfx.RESPAWN), "a life lost, the hull returns");
        assertFalse(rec.heard(Sfx.GAME_OVER));

        World w2 = world();
        rec.played.clear();
        w2.lives = 1;
        w2.ship.damage(9999, w2);
        assertTrue(rec.heard(Sfx.SHIP_DESTROYED) && rec.heard(Sfx.GAME_OVER), "the last life ends the run");
        assertFalse(rec.heard(Sfx.RESPAWN), "and there is no coming back");
    }

    @Test
    void dashBoostAndShieldEachSpeakOnTheirOwnEdge() {
        World w = world();
        Pad pad = new Pad();
        pad.tx = 1;
        pad.dash = true;
        w.update(1 / 60f, pad);
        assertTrue(rec.heard(Sfx.DASH));
        long dashes = rec.played.stream().filter(s -> s == Sfx.DASH).count();
        w.update(1 / 60f, pad); // held: the same dash does not ring again
        assertEquals(dashes, rec.played.stream().filter(s -> s == Sfx.DASH).count(), "one dash, one sound");

        pad.dash = false;
        pad.boost = true;
        pad.shield = true;
        w.update(1 / 60f, pad);
        assertTrue(rec.heard(Sfx.BOOST), "the boost lights once");
        assertTrue(rec.heard(Sfx.SHIELD_ON), "the shield rises once");
        pad.shield = false;
        w.update(1 / 60f, pad);
        assertTrue(rec.heard(Sfx.SHIELD_OFF), "and is let go");
    }

    // ---- what the world gives -----------------------------------------------------------------
    @Test
    void everyPickupHasItsOwnVoice() {
        World w = world();
        LootSystem loot = new LootSystem();
        Pickup coin = new Pickup();
        coin.type = Pickup.COIN;
        loot.collect(w, coin);
        assertTrue(rec.heard(Sfx.COIN));
        Pickup shard = new Pickup();
        shard.type = Pickup.SHARD;
        shard.variant = 0;
        loot.collect(w, shard);
        assertTrue(rec.heard(Sfx.SHARD));
        Pickup heal = new Pickup();
        heal.type = Pickup.HEALTH;
        loot.collect(w, heal);
        assertTrue(rec.heard(Sfx.HEALTH));
        Pickup band = new Pickup();
        band.type = Pickup.PIECE;
        band.variant = 0;
        loot.collect(w, band);
        assertTrue(rec.heard(Sfx.PIECE));
    }

    @Test
    void theFifthShardRingsTheForgeNotAShard() {
        World w = world();
        LootSystem loot = new LootSystem();
        for (int i = 0; i < 5; i++) {
            Pickup s = new Pickup();
            s.type = Pickup.SHARD;
            s.variant = 0;
            loot.collect(w, s);
        }
        assertTrue(rec.heard(Sfx.FORGE), "forging the lance has its own moment");
    }

    @Test
    void theAstraGathersWithItsOwnSound() {
        World w = world();
        for (int i = 0; i < 5; i++) w.hangar.addShard(0, 0);
        assertTrue(w.useForge());
        assertTrue(rec.heard(Sfx.ASTRA_WIND), "the gathering is heard");
        for (int i = 0; i < 400 && !rec.heard(Sfx.ASTRA_RELEASE); i++) w.update(1 / 60f, new Pad());
        assertTrue(rec.heard(Sfx.ASTRA_RELEASE), "and the release lets go");
    }

    @Test
    void theBayLocksTheHullOnce() {
        World w = world();
        float[] s = w.shops[0];
        w.ship.x = s[0];
        w.ship.y = s[1] - 44;
        w.update(1 / 60f, new Pad());
        assertTrue(rec.heard(Sfx.DOCK));
    }

    @Test
    void aRoundThatStrikesRockWhinesAway() {
        World w = world();
        float rx = -1;
        final float ry = 3000f; // inside Atala: Bhu Loka is open sky
        for (float x = 64; x < w.width - 64 && rx < 0; x += 16) if (w.blockedPx(x, ry)) rx = x;
        assertTrue(rx > 0, "the test world has rock at this height");
        w.ship.x = rx;
        w.ship.y = ry;
        vajra.weapon.Bullet b = w.obtainBullet();
        b.init(rx, ry, 0f, WeaponDef.SINGLE, 1f, 1f, false);
        b.update(w, 0.001f);
        assertTrue(rec.heard(Sfx.IMPACT), "your round on rock has its own sound");

        Rec foes = new Rec();
        Audio.use(foes);
        vajra.weapon.Bullet h = w.obtainBullet();
        h.init(rx, ry, 0f, WeaponDef.SINGLE, 1f, 1f, true);
        h.update(w, 0.001f);
        assertFalse(foes.heard(Sfx.IMPACT), "a foe's round on rock stays quiet, there are too many of them");
    }

    // ---- the engine and the warning -----------------------------------------------------------
    @Test
    void theEngineRisesWithPushSpeedAndBoost() {
        assertTrue(Ambience.engineTarget(0, 0, false) > 0f, "it idles");
        assertTrue(Ambience.engineTarget(1, 0, false) > Ambience.engineTarget(0, 0, false));
        assertTrue(Ambience.engineTarget(0, 1, false) > Ambience.engineTarget(0, 0, false));
        assertTrue(Ambience.engineTarget(1, 1, true) > Ambience.engineTarget(1, 1, false));
        assertTrue(Ambience.engineTarget(9, 9, true) <= 1f, "and never past full");
    }

    @Test
    void theEngineRunsInFlightAndFallsSilentWhenAMenuOpens() {
        World w = world();
        Ambience a = new Ambience();
        a.update(w, 1 / 60f, true);
        assertTrue(rec.engine > 0, "the engine idles in flight");
        int before = rec.stops;
        a.update(w, 1 / 60f, false);
        assertTrue(rec.stops > before, "a menu hushes the loops");
    }

    @Test
    void aFailingHullBeepsFasterTheLowerItGets() {
        World w = world();
        Ambience a = new Ambience();
        w.ship.hp = w.ship.maxHp();
        a.update(w, 1 / 60f, true);
        assertFalse(rec.heard(Sfx.LOW_HULL), "a healthy hull is quiet");
        w.ship.hp = w.ship.maxHp() * 0.2f;
        a.update(w, 1 / 60f, true);
        assertTrue(rec.heard(Sfx.LOW_HULL), "a failing hull warns at once");
        assertTrue(Ambience.warnGap(0.05f) < Ambience.warnGap(0.24f), "nearer death, faster beeps");
    }

    // ---- the player's own arithmetic ----------------------------------------------------------
    @Test
    void coinsClimbAWholeToneEachUpToSeven() {
        assertEquals(1f, SfxPlayer.coinPitch(0), 0.0001f);
        assertEquals(Math.pow(2, 2 / 12.0), SfxPlayer.coinPitch(1), 0.0001);
        assertTrue(SfxPlayer.coinPitch(3) > SfxPlayer.coinPitch(2));
        assertEquals(SfxPlayer.coinPitch(7), SfxPlayer.coinPitch(20), 0.0001f, "the ladder tops out");
    }

    @Test
    void aSoundCannotMachineGunItself() {
        assertFalse(SfxPlayer.gapOpen(10, Sfx.SHOT_MAGNUM.minGapMs));
        assertTrue(SfxPlayer.gapOpen(Sfx.SHOT_MAGNUM.minGapMs, Sfx.SHOT_MAGNUM.minGapMs));
        assertTrue(SfxPlayer.wander(Sfx.SHOT_MAGNUM) > 0, "shots vary a little");
        assertEquals(0f, SfxPlayer.wander(Sfx.UI_SELECT), 0f, "an instrument does not");
        for (Sfx s : Sfx.values()) assertTrue(s.minGapMs >= 0 && s.gain > 0 && s.gain <= 1f, s.name());
    }
}
