package vajra.enemy;

import vajra.audio.Sfx;

import vajra.weapon.WeaponDef;
import vajra.world.Effect;
import vajra.world.Popups;
import vajra.world.TileMap;
import vajra.world.World;

import java.util.Random;

/** Bala's brain. One state machine keyed off hp:
 *  phase 1 THE COMMANDER (100-66%): holds the pit, slow ordered volleys — his retinue rings him
 *      and soaks the shots; every volley release is the order for the ring to fire together.
 *  phase 2 THE DUEL (66-33%): stands whoever is left down, then circles at 420 px cycling
 *      volley -> spiral -> mortar -> shockwave.
 *  phase 3 BERSERK (33-0%): blinks to a new berth every few seconds — each vanish drops an
 *      elite pair at the old post (cap 6, they re-form his ring) — faster cycle, double spiral,
 *      and below 15% the spiral never stops.
 *  Writes vx/vy only, like Brain; fires through World with its own shot rows. */
public class BossBrain {
    static final int VOLLEY = 0, SPIRAL = 1, MORTAR = 2, SHOCK = 3;

    static final WeaponDef VOLLEY_SHOT = WeaponDef.mk("bala volley")
            .speed(330).damage(10).bulletSize(5).life(4).colour(1f, 0.78f, 0.2f);
    static final WeaponDef SPIRAL_SHOT = WeaponDef.mk("bala spiral")
            .speed(300).damage(9).bulletSize(4).life(4).colour(1f, 0.6f, 0.15f);
    static final WeaponDef SHELL_SHOT = WeaponDef.mk("bala shell")
            .speed(280).damage(14).bulletSize(10).life(4).colour(1f, 0.4f, 0.1f);
    static final WeaponDef SHOCK_SHOT = WeaponDef.mk("bala shock")
            .speed(340).damage(11).bulletSize(5).life(3.5f).colour(1f, 0.9f, 0.4f);

    private static final float PIT_X = World.PIT_X, PIT_Y = World.PIT_Y; // arena centre: the commander's post
    private static final float DUEL_R = 420;
    private static final Random rng = new Random(9);

    public static void think(Enemy e, World w, float dt, float t) {
        // threshold crossings are beats: stagger, ring effect, screen trauma
        float frac = e.hp / e.maxHp;
        if (!e.bossHidden && e.bossPhase == 1 && frac <= 2f / 3f) beginPhase(e, w, 2);
        else if (!e.bossHidden && e.bossPhase == 2 && frac <= 1f / 3f) beginPhase(e, w, 3);

        if (e.bossHidden) { // mid-blink: countdown, then reappear
            e.bossTimer -= dt;
            if (e.bossTimer <= 0) reappear(e, w);
            return;
        }

        vajra.ship.Ship hull = w.nearestHull(e.x, e.y);
        e.facing = (float) Math.atan2(hull.y - e.y, hull.x - e.x);
        if (e.bossInvuln) {
            e.bossTimer -= dt;
            e.hitFlash = 0.1f; // white strobe reads as the stagger
            if (e.bossTimer <= 0) {
                e.bossInvuln = false;
                e.bossTimer = 0.8f; // grace before his first attack of the phase
                e.bossTelegraph = false;
            }
        } else {
            e.bossTimer -= dt;
            if (e.bossTimer <= 0) step(e, w);
            if (e.bossPhase == 3) {
                e.bossBlink -= dt;
                if (e.bossBlink <= 0) vanish(e, w);
            }
        }
        move(e, w, dt);
    }

    /** Attack state machine: pick -> telegraph -> release, spiral streams in between. */
    private static void step(Enemy e, World w) {
        vajra.ship.Ship hull = w.nearestHull(e.x, e.y);
        if (e.bossPattern == SPIRAL && !e.bossTelegraph && e.bossStream > 0) {
            w.spawnEnemyBullet(e.x, e.y, e.bossAngle, SPIRAL_SHOT);
            if (e.bossPhase == 3) w.spawnEnemyBullet(e.x, e.y, e.bossAngle + (float) Math.PI, SPIRAL_SHOT);
            e.bossAngle += 0.4f;
            e.bossStream--;
            e.bossTimer = 0.09f;
            if (e.bossStream == 0) e.bossTimer = e.bossPhase == 3 ? 0.8f : 1.1f; // stream over: breathe
            return;
        }
        if (e.bossTelegraph) {
            if (!w.clearLine(e.x, e.y, hull.x, hull.y)) { // hold the tell until he can see you
                e.bossTimer = 0.5f;
                return;
            }
            release(e, w);
            if (e.bossStream > 0) return; // spiral: the stream branch takes over
            e.bossTelegraph = false;
            e.bossTimer = e.bossPhase == 1 ? 1.6f : e.bossPhase == 2 ? 1.3f : 0.9f;
        } else {
            e.bossPattern = pick(e);
            e.bossTelegraph = true;
            e.bossTimer = e.bossPattern == MORTAR ? 0.9f : e.bossPattern == VOLLEY ? 0.7f : 0.6f;
            if (e.bossPattern == MORTAR) { // the marker shows exactly where the shell lands
                e.bossMx = hull.x;
                e.bossMy = hull.y;
                w.addEffect(Effect.ring(e.bossMx, e.bossMy, 70, 1f, 0.5f, 0.2f));
            }
            if (e.bossPattern == SHOCK) e.bossAngle = rng.nextFloat() * 6.283f;
            if (e.bossPattern == SPIRAL)
                e.bossAngle = (float) Math.atan2(hull.y - e.y, hull.x - e.x);
        }
    }

    private static int pick(Enemy e) {
        if (e.bossPhase == 1) return VOLLEY; // the commander only issues orders
        if (e.hp < e.maxHp * 0.15f) return SPIRAL; // desperation: it never stops spinning
        return (e.bossPattern + 1) & 3; // cycle volley -> spiral -> mortar -> shock
    }

    private static void release(Enemy e, World w) {
        switch (e.bossPattern) {
            case VOLLEY: {
                vajra.ship.Ship hull = w.nearestHull(e.x, e.y);
                float a = (float) Math.atan2(hull.y - e.y, hull.x - e.x);
                for (int i = -2; i <= 2; i++)
                    w.spawnEnemyBullet(e.x, e.y, a + i * 0.17f, VOLLEY_SHOT);
                if (e.bossPhase == 1) orderVolley(e, w); // his shot is the signal
                break;
            }
            case SPIRAL:
                e.bossTelegraph = false;
                e.bossStream = e.bossPhase == 3 ? 22 : 16;
                e.bossTimer = 0.1f;
                break;
            case MORTAR:
                w.spawnEnemyBullet(e.x, e.y,
                        (float) Math.atan2(e.bossMy - e.y, e.bossMx - e.x), SHELL_SHOT);
                break;
            case SHOCK: // radial burst with two dodge lanes
                for (int i = 0; i < 20; i++) {
                    float a = i * (float) (Math.PI * 2 / 20);
                    float d = World.wrapAngle(a - e.bossAngle);
                    if (Math.abs(d) < 0.42f || Math.abs(Math.abs(d) - (float) Math.PI) < 0.42f) continue;
                    w.spawnEnemyBullet(e.x, e.y, a, SHOCK_SHOT);
                }
                break;
            default:
                break;
        }
    }

    /** Phase 1 only: every volley release fires his whole ring in the same beat. */
    private static void orderVolley(Enemy e, World w) {
        int i = 0;
        for (Enemy o : w.enemies) {
            if (o.dead || o.dismissed || o.commander != e) continue;
            o.active = true;
            o.fireTimer = Math.min(o.fireTimer, 0.03f * i++);
        }
    }

    private static void beginPhase(Enemy e, World w, int phase) {
        e.bossPhase = phase;
        w.sound(Sfx.BOSS_PHASE);
        e.bossInvuln = true;
        e.bossTimer = 1.5f;
        e.bossTelegraph = false;
        e.bossStream = 0;
        w.addEffect(Effect.ring(e.x, e.y, 320, 1f, 0.84f, 0.3f));
        w.ship.trauma = Math.min(1f, w.ship.trauma + 0.35f);
        if (phase == 2) { // the honorable duel: whoever still follows him is stood down
            w.popups.push(new Popups.Spec().text("STAND DOWN. THIS ONE IS MINE.").img("bala")
                    .at(e.x, e.y + 70).color(1f, 0.78f, 0.2f).scale(1.4f).life(2.4f).rise(0));
            for (Enemy o : w.enemies) {
                if (o.dead || o.commander != e) continue;
                o.dismissed = true;
                o.commander = null;
            }
        } else {
            w.popups.push(new Popups.Spec().text("THEN WE ALL BURN TOGETHER.").img("bala")
                    .at(e.x, e.y + 70).color(1f, 0.5f, 0.12f).scale(1.4f).life(2.4f).rise(0));
            e.bossBlink = 3f; // first blink three seconds into the berserk
        }
    }

    private static void vanish(Enemy e, World w) {
        e.bossHidden = true;
        e.bossTimer = 0.7f;
        e.bossTelegraph = false;
        e.bossStream = 0;
        w.addEffect(Effect.ring(e.x, e.y, 150, 1f, 0.84f, 0.3f)); // afterimage at the old post
        w.addEffect(Effect.spark(e.x + 34, e.y, 1f, 0.84f, 0.3f));
        w.addEffect(Effect.spark(e.x - 34, e.y, 1f, 0.84f, 0.3f));
        w.summonElites(e.x, e.y, e); // the pair he leaves behind
    }

    private static void reappear(Enemy e, World w) {
        float[] p = w.bossBlinkSpot();
        e.x = p[0];
        e.y = p[1];
        e.bossHidden = false;
        e.bossTimer = 0.6f; // arrival beat: hittable, but he shoots after it
        e.bossTelegraph = false;
        e.bossBlink = 5f + rng.nextFloat() * 2f;
        e.hitFlash = 0.3f;
        w.addEffect(Effect.ring(e.x, e.y, 160, 1f, 0.84f, 0.3f));
    }

    private static void move(Enemy e, World w, float dt) {
        vajra.ship.Ship hull = w.nearestHull(e.x, e.y);
        float dx = hull.x - e.x, dy = hull.y - e.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float gx = 0, gy = 0;
        if (e.bossPhase == 1) { // holds the pit — the commander does not chase
            float hx = e.x - PIT_X, hy = e.y - PIT_Y;
            float hd = (float) Math.sqrt(hx * hx + hy * hy);
            if (hd > 40) {
                gx -= hx / hd * 60;
                gy -= hy / hd * 60;
            }
            if (dist < 340 && dist > 1) { // and does not hug either
                gx += dx / dist * 70;
                gy += dy / dist * 70;
            }
        } else if (e.bossPhase == 2) { // circles at duel range, tangent + radial correction
            if (dist > 1) {
                float radial = (dist - DUEL_R) / Math.max(1, dist);
                gx = -dy / dist * 130 + dx / dist * radial * 160;
                gy = dx / dist * 130 + dy / dist * radial * 160;
            }
        } else if (dist > 1) { // berserk: drifts in slowly between blinks
            gx = dx / dist * 90;
            gy = dy / dist * 90;
        }
        float gs = (float) Math.sqrt(gx * gx + gy * gy);
        if (gs > 1) { // whiskers: shy off rock, Brain's probe for the boss's own heading
            float fx = gx / gs, fy = gy / gs;
            if (w.blockedPx(e.x + fx * 60, e.y + fy * 60)) {
                gx -= fx * 260;
                gy -= fy * 260;
                gx += -fy * 180;
                gy += fx * 180;
            }
        }
        float max = e.bossPhase == 1 ? 70 : e.bossPhase == 2 ? 100 : 80;
        gs = (float) Math.sqrt(gx * gx + gy * gy);
        if (gs > max) {
            gx = gx / gs * max;
            gy = gy / gs * max;
        }
        float k = 1 - (float) Math.exp(-4f * dt);
        e.vx += (gx - e.vx) * k;
        e.vy += (gy - e.vy) * k;
    }
}
