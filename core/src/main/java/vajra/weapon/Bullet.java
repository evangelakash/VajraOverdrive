package vajra.weapon;

import vajra.audio.Sfx;

import vajra.enemy.Enemy;
import vajra.world.Effect;
import vajra.world.World;

/** One projectile, player or enemy. Pooled — never construct directly, use World.spawnBullet
 *  or World.spawnEnemyBullet. Hits are resolved in World.update, not here. */
public class Bullet {
    public float x, y, vx, vy;
    public float damage, radius, life;
    public float homingTurn; // rad/s
    public float aoe;
    public boolean pierceWalls, pierceEnemies, hostile, dead;
    public float r, g, b;
    public WeaponDef def; // the pattern this was fired from — renderer picks the sprite by it
    public int owner = -1; // the roster id of the seat this round belongs to (co-op books)

    /** One stat row for enemy fire, so both sides share the projectile code. */
    public static final WeaponDef HOSTILE = WeaponDef.mk("enemy shot")
            .speed(270f).damage(8f).bulletSize(4f).life(3.5f).colour(1f, 0.42f, 0.3f);

    /** power drives speed and damage (charge); dmgMult is the ship's damage tier only — it must
     *  never touch velocity, or buying a damage upgrade silently speeds the bullet up. */
    public void init(float x, float y, float angle, WeaponDef def, float power, float dmgMult, boolean hostile) {
        this.x = x;
        this.y = y;
        float sp = def.speed * (1 + 0.4f * (power - 1));
        this.vx = (float) Math.cos(angle) * sp;
        this.vy = (float) Math.sin(angle) * sp;
        this.damage = def.damage * power * dmgMult;
        this.radius = def.bulletSize;
        this.life = def.life;
        this.homingTurn = (float) Math.toRadians(def.homingTurn);
        this.aoe = def.aoeRadius;
        this.pierceWalls = def.pierceWalls;
        this.pierceEnemies = def.pierceEnemies;
        this.hostile = hostile;
        this.def = def;
        this.r = def.r;
        this.g = def.g;
        this.b = def.b;
        this.dead = false;
        this.owner = -1; // the spawner (or the wire) claims it right after
    }

    public void update(World w, float dt) {
        if (!hostile && homingTurn > 0) steer(w, dt);
        x += vx * dt;
        y += vy * dt;
        life -= dt;
        if (life <= 0) {
            expire(w);
            return;
        }
        if (x < 0 || x > w.width || y < 0 || y > w.height) {
            dead = true;
            return;
        }
        if (w.blockedPx(x, y)) {
            // enemy fire is stopped by rock too: cover works both ways
            if (hostile || !pierceWalls) {
                if (!hostile) w.sound(Sfx.IMPACT, x, y); // your round strikes rock and whines away
                expire(w);
            }
        }
    }

    private void steer(World w, float dt) {
        Enemy best = null;
        float bestD = 700f * 700f;
        for (Enemy e : w.enemies) {
            if (e.dead) continue;
            float dx = e.x - x, dy = e.y - y;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) return;
        float desired = (float) Math.atan2(best.y - y, best.x - x);
        float cur = (float) Math.atan2(vy, vx);
        float max = homingTurn * dt;
        float turn = Math.max(-max, Math.min(max, World.wrapAngle(desired - cur)));
        float sp = (float) Math.sqrt(vx * vx + vy * vy);
        float na = cur + turn;
        vx = (float) Math.cos(na) * sp;
        vy = (float) Math.sin(na) * sp;
    }

    /** Boom. Only the player's shots leave a mark; splash goes through World so hits live in one place. */
    public void detonate(World w) {
        dead = true;
        if (hostile) return;
        w.shooter = owner < 0 ? 0 : owner; // an unstamped blast is this seat's own
        if (aoe > 0) {
            w.sound(Sfx.EXPLODE_M, x, y);
            w.areaDamage(x, y, aoe, damage);
            w.addEffect(Effect.ring(x, y, aoe, r, g, b));
        } else {
            w.spray(x, y, vx, vy, r, g, b);
        }
    }

    private void expire(World w) {
        detonate(w);
    }
}