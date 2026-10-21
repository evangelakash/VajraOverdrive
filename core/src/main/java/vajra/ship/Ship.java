package vajra.ship;

import vajra.audio.Sfx;

import vajra.weapon.Weapon;
import vajra.world.Effect;
import vajra.world.World;

/** The player ship. Screen-relative flight: WASD pushes along the world axes, so D is always
 *  right and W is always up — and it never turns the ship. The nose is a separate thing: it
 *  follows the cursor, or Q/E, and otherwise holds its heading.
 *  Meters: boost energy, dash charge + i-frames, shield energy, hull. Centre-based coordinates. */
public class Ship {
    public static final float W = 24, H = 24;
    private static final float THRUST_EASE = 18f; // input ramps over ~1/18 s: taps and flips glide
    private static final float AIM_EASE = 15f;    // the nose glides onto the cursor, never whips
    private static final float DASH_SPEED = 1600;
    public static final float DASH_TIME = 0.3f;
    /** Thrust multiplier while the Astra winds up. A third is enough to drift out of trouble
     *  and far too slow to dodge properly - the gap where the charge gets dangerous. */
    public static final float WIND_THRUST = 0.33f;
    private static final float DASH_IFRAMES = 0.4f;
    // The dash hands back a slice of the speed you had going in, so a burst flows into the
    // next push instead of dumping the ship to a dead stop at the end of the smear.
    private static final float DASH_EXIT = 0.55f;
    // Boost: 4.2s of use from full, ~14s back. Enough to cross a duel or cross a band, short
    // enough that spending it is a decision (it used to be 2.2s of use against a 20s wait).
    private static final float BOOST_DRAIN = 24f;
    private static final float BOOST_REGEN = 7f;
    // Shield cost scales with what got through: a Hataka pellet is cheap to eat, a Bhavi
    // shell is not. The meter now has a relationship with the threat instead of a flat toll.
    private static final float SHIELD_PER_DAMAGE = 1f;
    private static final float SHIELD_MIN_COST = 5f;
    private static final float DIP_TIME = 0.35f; // how long a hit/shield keeps the hull pressed down

    public final Hangar hangar;
    public ShipDef def;
    public Weapon weapon = new Weapon();

    public float x, y;
    public float vx, vy;
    public float faceGoal = Float.NaN; // a mirrored hull: the heading its pilot last reported
    public float facing = (float) (Math.PI / 2); // radians, PI/2 = north (up) — the rest heading
    public float hp;
    public float boostEnergy = 100;
    public float shieldEnergy;
    public boolean shieldOn;
    public boolean boosting;
    public float thrustX, thrustY; // last thrust input — the plume hangs against it
    private float moveX, moveY;    // eased thrust actually pushing this frame
    public float thrustHold;       // seconds the current thrust has been held; drives the plume ramp
    public float dashCd, dashTime, iFrames, invuln;
    private float exhaustT, smokeT; // particle cadence: exhaust while pushing, smoke when dying
    public float dashDirX, dashDirY; // unit push direction the last dash took; steers the deform
    private float preVx, preVy;     // velocity going into the dash, handed back on the way out
    public float dip;               // 0..1 hull press-down: hit / shield / dash, no halo needed
    public float dipT;
    public float trauma;
    /** True while the Akasha Astra is winding up. The renderer dips the hull further on this. */
    public boolean winding;

    private boolean prevDash, prevFire;

    public Ship(float x, float y, ShipDef def, Hangar hangar) {
        this.x = x;
        this.y = y;
        this.def = def;
        this.hangar = hangar;
        this.hp = maxHp();
        this.shieldEnergy = shieldCap();
    }

    public void setDef(ShipDef def) {
        this.def = def;
        hp = maxHp();
        shieldEnergy = shieldCap();
    }

    public void update(float dt, ShipControls in, World w) {
        trauma = Math.max(0, trauma - 1.6f * dt);
        if (invuln > 0) invuln -= dt;
        if (iFrames > 0) iFrames -= dt;
        if (dashCd > 0) dashCd -= dt;
        boolean wasDashing = dashTime > 0; // the burst ended this frame: hand the speed back
        if (dashTime > 0) dashTime -= dt;
        if (dipT > 0) dipT -= dt;
        // THE DIP: the hull sits a few px low while it is hit, shielding or bursting, and
        // eases back up. It replaces the state ring — feedback without a halo around the ship.
        float dipWant = (dipT > 0 || dashTime > 0 || shieldOn) ? 1f : 0f;
        dip += (dipWant - dip) * (1f - (float) Math.exp(-16f * dt));

        boolean wasShield = shieldOn, wasBoost = boosting;
        shieldOn = in.shield() && shieldEnergy > 0;
        if (shieldOn && !wasShield) w.sound(Sfx.SHIELD_ON);
        shieldEnergy += shieldOn ? -18f * dt : 10f * dt;
        shieldEnergy = Math.max(0, Math.min(shieldCap(), shieldEnergy));
        if (wasShield && (!shieldOn || shieldEnergy <= 0))
            w.sound(shieldEnergy <= 0 ? Sfx.SHIELD_DOWN : Sfx.SHIELD_OFF); // let go, or run dry

        // THE CHARGE HAS WEIGHT. While the Astra winds up the hull is heavy: thrust is cut to a
        // third and the dash is locked out. Not "you cannot move" - that reads as a bug - but
        // "you are committed and slow", which is what makes the 1.75 s a decision instead of a
        // pause. Nothing else in the game takes the dash away, so losing it here is felt.
        winding = w.winding();
        float tx = in.thrustX(), ty = in.thrustY();
        if (winding) { tx *= WIND_THRUST; ty *= WIND_THRUST; }
        thrustX = tx;
        thrustY = ty;
        if (tx != 0 || ty != 0) thrustHold += dt;
        else thrustHold = 0;
        boosting = in.boost() && boostEnergy > 0 && (tx != 0 || ty != 0);
        if (boosting && !wasBoost) w.sound(Sfx.BOOST);
        boostEnergy += boosting ? -BOOST_DRAIN * dt : BOOST_REGEN * dt;
        boostEnergy = Math.max(0, Math.min(100, boostEnergy));

        // Nose: the cursor wins, then Q/E. Thrust never turns the ship — WASD is pure translation.
        if (in.mouseAim()) {
            float aim = (float) Math.atan2(in.aimY() - y, in.aimX() - x);
            facing += World.wrapAngle(aim - facing) * (1f - (float) Math.exp(-AIM_EASE * dt));
        } else if (in.rotate() != 0) {
            facing += in.rotate() * turnRate() * dt;
        }

        // THRUST EASE: the push ramps in and out instead of snapping, so taps and direction
        // changes glide — the ship never lurches. The dash and the plume use the raw input.
        float ease = 1f - (float) Math.exp(-THRUST_EASE * dt);
        moveX += (tx - moveX) * ease;
        moveY += (ty - moveY) * ease;

        boolean d = in.dash() && !winding;      // the charge owns the dash
        if (d && !prevDash && dashCd <= 0) {
            dashTime = DASH_TIME;
            w.sound(Sfx.DASH);
            // the cooldown starts when the burst ENDS, not when it begins: the smear is not
            // dead time, it is 0.3s of free movement, and the gap after it is what you wait.
            iFrames = Math.max(iFrames, DASH_IFRAMES);
            float dx = tx, dy = ty, m = (float) Math.sqrt(dx * dx + dy * dy);
            if (m < 0.01f) { // no input: burst along the nose
                dx = (float) Math.cos(facing);
                dy = (float) Math.sin(facing);
                m = 1f;
            }
            dashDirX = dx / m;
            dashDirY = dy / m;
            preVx = vx;
            preVy = vy;
            trauma = Math.min(1, trauma + 0.25f);
        }
        prevDash = d;
        if (wasDashing && dashTime <= 0) dashCd = dashCd(); // the wait starts at the tail of the smear

        float a = accel() * (boosting ? boostMult() : 1f);
        if (dashTime <= 0) {
            if (wasDashing) { vx = preVx * DASH_EXIT; vy = preVy * DASH_EXIT; }
            // drag before thrust, with the rate tuned to the stat itself (accel/maxSpeed):
            // coasting converges exactly on maxSpeed(), so the SPD figure and the speed
            // upgrades are real numbers instead of decoration
            float ms = maxSpeed();
            float fr = accel() / ms;
            vx -= vx * fr * dt;
            vy -= vy * fr * dt;
            vx += moveX * a * dt; // screen up and world up are the same direction: y-up
            vy += moveY * a * dt;
            float maxSp = ms * (boosting ? boostMult() : 1f);
            float sp = (float) Math.sqrt(vx * vx + vy * vy);
            if (sp > maxSp && sp > 0) {
                vx = vx / sp * maxSp;
                vy = vy / sp * maxSp;
            }
        } else {
            vx = dashDirX * DASH_SPEED * dashEase(dashTime);
            vy = dashDirY * DASH_SPEED * dashEase(dashTime);
        }

        x += vx * dt;
        y += vy * dt;
        x = Math.max(0, Math.min(w.width, x));
        y = Math.max(0, Math.min(w.height, y));
        float[] c = w.collide(x - W / 2, y - H / 2, W, H);
        x = c[0] + W / 2;
        y = c[1] + H / 2;

        // facing is already the cursor angle when aiming at the mouse, so it is also the shot line
        boolean f = in.fire();
        if (f) weapon.held(dt, facing, this, w);
        if (!f && prevFire) weapon.released(this, w);
        prevFire = f;
        weapon.update(dt, this, w, f);

        // EXHAUST: a dense burst of tiny plume-coloured motes from behind the hull — fast, fanned
        // at random around the tail axis, each riding its own diminishing sine. Never during the
        // dash (the ghost rule: ShipTest.dashLeavesNoParticles).
        exhaustT -= dt;
        if ((tx != 0 || ty != 0) && dashTime <= 0 && exhaustT <= 0) {
            exhaustT = boosting ? 0.006f : 0.01f; // a lot of them: 100-166 spawns/s
            float rear = (float) Math.atan2(-Math.sin(facing), -Math.cos(facing));
            float rx = x + (float) Math.cos(rear) * (H / 2 + 6);
            float ry = y + (float) Math.sin(rear) * (H / 2 + 6);
            // plume colour: the ship's tint, hue-shifted 75% toward crimson while boosting —
            // the exact mix ShipRenderer paints the flame with
            float k = boosting ? 0.75f : 0f;
            float cr = def.r + (0.92f - def.r) * k;
            float cg = def.g + (0.16f - def.g) * k;
            float cb = def.b + (0.22f - def.b) * k;
            float ang = rear + (w.fxRng.nextFloat() - 0.5f); // random spread, behind the hull
            float sp = 120 + w.fxRng.nextFloat() * 80;       // fast
            Effect p = Effect.spark(rx, ry,
                    (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp,
                    2.5f, 0.35f, 1, cr, cg, cb);
            p.phase = w.fxRng.nextFloat() * 6.2831853f;    // own phase: no lockstep line
            p.wobble = 60 + w.fxRng.nextFloat() * 60;       // diminishing sideways sine
            w.addEffect(p);
        }
        // CRITICAL HULL: 4-frame sprite puffs from that same rear point until repaired or lost
        smokeT -= dt;
        if (hp > 0 && hp < maxHp() * 0.3f && smokeT <= 0) {
            smokeT = 0.12f;
            float ux = -(float) Math.cos(facing), uy = -(float) Math.sin(facing);
            w.addEffect(Effect.smoke(x + ux * (H / 2 + 6), y + uy * (H / 2 + 6),
                    ux * 25, uy * 25, 0.8f, 0.8f, 0.85f));
        }
    }

    /** Dash curve, shared by burst speed and hull smear: full thrust from the first frame,
     *  held through the front half of the dash, then eased to a stop over the back half. */
    public static float dashEase(float dashTime) {
        float t = 1f - dashTime / DASH_TIME;
        return t < 0.5f ? 1f : 4f * (1f - t) * (1f - t);
    }

    public void damage(float amount, World w) {
        if (w.gameOver) return;
        if (invuln > 0 || iFrames > 0) return;
        if (shieldOn && shieldEnergy > 0) {
            // scaled to what actually landed: a 5-damage betray ring is cheap to eat, a
            // 14-damage Bhavi shell costs nearly three times as much
            shieldEnergy = Math.max(0, shieldEnergy - Math.max(SHIELD_MIN_COST, amount * SHIELD_PER_DAMAGE));
            trauma = Math.min(1, trauma + 0.3f);
            dipT = DIP_TIME;
            w.addEffect(Effect.ring(x, y, 40, 0.5f, 0.8f, 1f)); // ripple across the bubble
            w.sound(Sfx.SHIELD_HIT);
            return;
        }
        float actual = amount * (1f - def.damageReduction); // tank hulls ignore a fraction
        hp -= actual;
        invuln = 0.6f;
        trauma = Math.min(1, trauma + 0.6f);
        dipT = DIP_TIME;
        if (hp > 0) w.sound(Sfx.HULL_HIT);
        if (hp <= 0) {
            w.sound(Sfx.SHIP_DESTROYED);
            w.score.breakCombo(); // death drops the chain before anything else
            trauma = 1;
            w.lives = Math.max(0, w.lives - 1);
            if (w.lives == 0) {
                w.gameOver = true; // all lives spent: the hull stays where it fell, the screen freezes
                w.sound(Sfx.GAME_OVER);
                hp = 0;
                trauma = 0;
                return;
            }
            hp = maxHp();
            float[] a = w.respawnAnchor(); // most recent checkpoint, else the region entrance
            x = a[0];
            y = a[1];
            vx = 0;
            vy = 0;
            invuln = 1.5f; // breathing room to fly out of whatever killed you
            w.sound(Sfx.RESPAWN);
        }
    }

    public float maxHp() { return def.maxHp + hangar.hullBonus(def); }
    public float accel() { return def.accel; }
    public float maxSpeed() { return def.maxSpeed + hangar.speedBonus(def); }
    public float turnRate() { return (float) Math.toRadians(def.turnRate + hangar.turnBonus(def)); }
    public float boostMult() { return def.boostMult; }
    public float dashCd() { return def.dashCd * hangar.dashCdMult(def); }
    public float shieldCap() { return 50 + hangar.shieldBonus(def); }
    public float cooldownMult() { return hangar.cooldownMult(def); }
    public float damageMult() { return hangar.damageMult(def); }
}
