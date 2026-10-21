package vajra.world;

import vajra.audio.Sfx;

import vajra.enemy.Enemy;

/** Enemy director: the boss seal, the commander's call, mid-berserk deployments and his blink
 *  berths. Extracted from World; the enemy roster itself stays on World for rendering. */
public final class EnemyDirector {
    private final World world;

    public EnemyDirector(World world) {
        this.world = world;
    }

    /** The seal lifts: Bala appears in his arena the moment the run's kill quota is full.
     *  The quota itself stays silent on screen — no banner, no count. */
    public void trySpawnBoss() {
        if (world.boss != null || !world.quotaMet()) return;
        world.boss = new Enemy(Enemy.Kind.BALA, World.PIT_X, World.PIT_Y);
        world.boss.boss = true;
        world.boss.rank = 1;
        world.add(world.boss);
        world.sound(Sfx.BOSS_AWAKEN);
        world.addEffect(Effect.ring(world.boss.x, world.boss.y, 340, 1f, 0.84f, 0.3f));
    }

    /** The call: the whole arena retinue leaves its post and forms the commander's ring. */
    public void orderRetinue(Enemy commander) {
        for (Enemy e : world.enemies) {
            if (e == commander || e.dead || e.homeY < world.regionStart[5]) continue;
            e.commander = commander;
            e.sentry = false;
            e.active = true;
        }
    }

    /** Mid-berserk deployment: a gold elite pair at his old post, straight into his ring (cap 6). */
    public void summonElites(float x, float y, Enemy commander) {
        int alive = 0;
        for (Enemy o : world.enemies)
            if (!o.dead && o.elite && o.commander == commander) alive++;
        if (alive >= 6) return;
        for (float dx : new float[] { -44, 44 }) {
            Enemy m = new Enemy(Enemy.Kind.VAKRA, x + dx, y - 36);
            m.elite = true;
            m.rank = 1;
            m.hp = m.maxHp = m.maxHp * 2;
            m.wakeRange = 1500;
            m.commander = commander;
            m.active = true;
            world.add(m);
        }
    }

    /** A random open berth in the arena for the boss's blink: 350-850 px from the ship. */
    public float[] bossBlinkSpot() {
        for (int i = 0; i < 40; i++) {
            float x = 300 + world.orbRng.nextFloat() * (world.width - 600);
            float y = 17900 + world.orbRng.nextFloat() * (world.height - 17900 - 250);
            float dx = x - world.ship.x, dy = y - world.ship.y;
            float d2 = dx * dx + dy * dy;
            if (d2 < 350 * 350 || d2 > 850 * 850) continue;
            if (world.spotClear(x, y)) return new float[] { x, y };
        }
        return new float[] { world.width / 2, 19104 };
    }
}
