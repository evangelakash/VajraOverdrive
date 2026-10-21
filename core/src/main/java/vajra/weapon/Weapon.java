package vajra.weapon;

import vajra.ship.Ship;
import vajra.world.World;

/** Fire controller: cooldown, burst scheduling, charge build. One per ship. */
public class Weapon {
    public WeaponDef def = WeaponDef.SINGLE;
    private float cooldown;
    private float charge;
    private float lastAim;
    private int burstLeft;
    private float burstTimer;

    /** firing is the live trigger state: a burst stops the moment you let go. */
    public void update(float dt, Ship s, World w, boolean firing) {
        cooldown -= dt;
        if (burstLeft > 0 && !firing) {
            burstLeft = 0;
            return;
        }
        if (burstLeft > 0) {
            burstTimer -= dt;
            if (burstTimer <= 0) {
                fire(lastAim, 1f, s, w);
                burstLeft--;
                burstTimer = def.burstGap;
            }
        }
    }

    public void held(float dt, float aim, Ship s, World w) {
        lastAim = aim;
        if (def.chargeTime > 0) {
            charge = Math.min(def.chargeTime, charge + dt);
            return;
        }
        if (cooldown <= 0 && burstLeft == 0) {
            fire(aim, 1f, s, w);
            cooldown = def.cooldown * s.cooldownMult();
        }
    }

    public void released(Ship s, World w) {
        if (def.chargeTime > 0 && cooldown <= 0 && charge > 0.12f) { // cooldown gates release too:
            fire(lastAim, 1f + charge / def.chargeTime, s, w);      // else tap-firing beats charging
            cooldown = def.cooldown * s.cooldownMult();
        }
        charge = 0;
    }

    public float chargeRatio() {
        return def.chargeTime > 0 ? charge / def.chargeTime : 0;
    }

    private void fire(float aim, float power, Ship s, World w) {
        float dmg = def.damage * power * s.damageMult();
        if (def.beam) {
            float a = w.assistAim(aim, def.aimAssist ? def.aimAssistDeg : 0);
            w.laser(s.x, s.y, a, def.range, def.r, def.g, def.b, dmg);
            return;
        }
        float a = w.assistAim(aim, def.aimAssist ? def.aimAssistDeg : 0);
        float mx = s.x + (float) Math.cos(a) * 22;
        float my = s.y + (float) Math.sin(a) * 22;
        for (int i = 0; i < def.count; i++) {
            float off = def.count == 1 ? 0 : (float) (i * def.spreadDeg / (def.count - 1) - def.spreadDeg / 2);
            w.spawnBullet(mx, my, a + (float) Math.toRadians(off), def, power, s.damageMult());
        }
        if (def.burst > 1 && burstLeft == 0) { // re-arm only from a fresh trigger pull —
            burstLeft = def.burst - 1;        // re-arming mid-burst loops it forever on burst 3+
            burstTimer = def.burstGap;
        }
    }
}