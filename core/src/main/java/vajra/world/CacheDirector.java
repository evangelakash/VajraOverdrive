package vajra.world;

import vajra.audio.Sfx;

import vajra.enemy.Enemy;

/** Hidden-cache director: touch and the local squad converges, the last fall pays out.
 *  Extracted from World, which still owns the cache positions for generation/tests. The stash
 *  is never announced or marked — the trespass ambush is meant to read as unexplained — and it
 *  pays the moment it's yours (no second touch on an invisible point).
 *
 *  <p>STATE IS PER PLAYER SEAT, not per band. Two ships in one band each own their own stash:
 *  opening yours does not open theirs, and a squad summoned by one is not another's. Solo is
 *  seat 0. */
public final class CacheDirector {
    private static final float CACHE_TOUCH = 56;  // ship centre to cache centre, px
    private static final float AMBUSH_R = 1000;   // only the local squad hears the cache, px

    private final World world;

    public CacheDirector(World world) {
        this.world = world;
    }

    /** Cache contact check, run every frame from World.update. */
    public void update() {
        for (int i = 0; i < world.caches.length; i++) {
            float dx = world.ship.x - world.caches[i][0];
            float dy = world.ship.y - world.caches[i][1];
            if (dx * dx + dy * dy <= CACHE_TOUCH * CACHE_TOUCH) touch(i);
        }
    }

    public void touch(int i) {
        int me = world.slot;
        if (world.cacheState[me][i] != World.CACHE_SEALED) return; // contested or paid: nothing to do
        float cx = world.caches[i][0], cy = world.caches[i][1];
        int n = 0;
        for (Enemy e : world.enemies) {
            if (e.dead || e.siteGuard || !inBand(e, i)) continue;
            float dx = e.x - cx, dy = e.y - cy;
            if (dx * dx + dy * dy > AMBUSH_R * AMBUSH_R) continue;
            e.hunt = true;
            e.active = true;
            n++;
        }
        if (n == 0) payout(i); // nobody answers: the stash is yours the moment you touch it
        else {
            world.cacheState[me][i] = World.CACHE_CONTESTED;
            world.sound(Sfx.CACHE_WAKE); // the cache fights back
        }
    }

    public void resolve() {
        int me = world.slot;
        if (me < 0) return;
        for (int i = 0; i < world.caches.length; i++) {
            if (world.cacheState[me][i] != World.CACHE_CONTESTED) continue;
            boolean alive = false;
            for (Enemy e : world.enemies)
                if (!e.dead && e.hunt && inBand(e, i)) { alive = true; break; }
            if (!alive) payout(i); // the last fall pays out on the spot
        }
    }

    /** The stash changes hands: 100 coins and a life, once, for THIS seat. The payment ring
     *  at the cache is the only mark it leaves — no label, no marker, no announcement. */
    private void payout(int i) {
        world.cacheState[world.slot][i] = World.CACHE_DONE;
        world.hangar.coins += 100;
        world.lives++;
        world.sound(Sfx.CACHE_OPEN);
        world.addEffect(Effect.ring(world.caches[i][0], world.caches[i][1], 160, 1f, 0.84f, 0.3f));
        world.addEffect(Effect.spark(world.caches[i][0], world.caches[i][1], 1f, 0.84f, 0.3f));
    }

    private boolean inBand(Enemy e, int band) { // cache 0..4 lives in regions 1..5
        int r = band + 1;
        return e.homeY >= world.regionStart[r] && e.homeY < world.regionEnd[r];
    }
}
