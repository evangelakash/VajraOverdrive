package vajra.world;

import vajra.enemy.Enemy;

/** One cube of the Akasha Astra: a shard of the weapon that leaves the hull, picks a foe and
 *  goes for it.
 *
 *  <p>It homes on its target and <b>goes through rock</b> - that is the point of it. A cube
 *  that needed a clear line would be a worse bullet; these are fired into a crowd and each one
 *  is answerable to exactly one enemy, so the Astra reaches through a chamber instead of only
 *  hitting what it can see.
 *
 *  <p>Rendering is done in the shape pass from the spin, not with a sprite: the cube's three
 *  visible faces are projected from a rotating 3D frame, which is what sells it as solid.
 *  {@link #spin} is world time, not a delta, so the rotation is frame-rate independent.
 */
public class AstraCube {
    public float x, y;
    public float vx, vy;
    public Enemy target;
    public float damage;
    /** The roster id whose volley this cube is (0 = this seat). A guest's release is armed
     *  on the hub, so the cube carries who fired it and the kill pays them. */
    public int owner;
    public float life = 4f;      // it gives up rather than chase forever
    public float spin;           // world time; drives the 3D tumble
    public float spinRate = 5.5f;
    public boolean dead;

    /** A cube is HELD while the Astra winds up, and ARMED once it lets go. Held cubes do not
     *  hunt: they hang in a shell around the hull, spiralling in as the charge builds. That
     *  cage of twenty turning solids is the whole tell - by the time it looks ready, it is. */
    public boolean armed = true;
    /** Angle around the ship while held, and the slot it holds so the shell stays evenly
     *  spaced instead of clumping. */
    public float orbit, slot;

    public AstraCube(float x, float y, float angle, float speed, Enemy target, float damage) {
        this.x = x;
        this.y = y;
        this.vx = (float) Math.cos(angle) * speed;
        this.vy = (float) Math.sin(angle) * speed;
        this.target = target;
        this.damage = damage;
    }

    /** The held state: ride a shrinking circle round the hull. `k` is charge progress 0..1 and
     *  drives both the radius and the tumble, so the shell tightens and spins up together. */
    public void hold(vajra.ship.Ship s, float dt, float k, float outerR, float innerR) {
        orbit += (2.2f + 9f * k) * dt;
        spin += (3f + 26f * k) * dt;          // the tumble winds up with the shell
        float r = outerR + (innerR - outerR) * k;
        x = s.x + (float) Math.cos(orbit + slot) * r;
        y = s.y + (float) Math.sin(orbit + slot) * r;
        life = 99f;                           // a held cube never runs out on its own
    }

    /** Send it on its way. Called once, at the release. */
    public void arm(float angle, float speed, Enemy target) {
        this.target = target;
        this.armed = true;
        this.life = 4f;
        this.vx = (float) Math.cos(angle) * speed;
        this.vy = (float) Math.sin(angle) * speed;
    }

    /** Steer toward the target, ignoring rock entirely. Returns true if it connected. */
    public boolean think(World w, float dt, float turnRate) {
        if (life <= 0) return false;
        life -= dt;
        if (target == null || target.dead) {
            // nothing to chase: keep the current heading and let it expire, or the caster
            // re-points it when it next fires
            x += vx * dt;
            y += vy * dt;
            return false;
        }
        float dx = target.x - x, dy = target.y - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        float reach = Math.max(target.w, target.h) * 0.5f + 6f;

        // close enough to bite
        if (d <= reach + speedOf() * dt) {
            target.hurt(damage, w, owner);
            return true;
        }
        float inv = d > 0.001f ? 1f / d : 0f;
        float wantX = dx * inv, wantY = dy * inv;
        float k = Math.min(1f, turnRate * dt);
        float v = speedOf();
        float nvx = vx + (wantX * v - vx) * k;
        float nvy = vy + (wantY * v - vy) * k;
        float nv = (float) Math.sqrt(nvx * nvx + nvy * nvy);
        if (nv > 0.001f) {
            vx = nvx / nv * v;
            vy = nvy / nv * v;
        }
        x += vx * dt;
        y += vy * dt;
        return false;
    }

    public float speedOf() { return (float) Math.sqrt(vx * vx + vy * vy); }
}