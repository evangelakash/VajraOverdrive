package vajra.audio;

import vajra.ship.Ship;
import vajra.world.World;

/** The sounds that are not events: the engine that never quite stops and the warning that will
 *  not let a failing hull go unheard. Driven once a frame from the run's screen, so the world
 *  stays free of any knowledge of how it is listened to.
 *
 *  <p>The engine idles low, rises with the push on the stick and with speed, and lifts in pitch
 *  under boost; it eases between levels so it swells instead of stepping. Below a quarter hull a
 *  double beep sounds, faster the closer to death. In a menu, the hangar, a pause or a finished
 *  run the loops are hushed. */
public final class Ambience {
    private float level;
    private float warn;

    /** Where the engine's level is heading: an idle floor, the push, the speed, and a lift for boost. */
    public static float engineTarget(float push, float speed, boolean boosting) {
        push = Math.max(0f, Math.min(1f, push));
        speed = Math.max(0f, Math.min(1f, speed));
        return Math.min(1f, 0.10f + 0.55f * push + 0.25f * speed + (boosting ? 0.15f : 0f));
    }

    /** Seconds between the low-hull beeps: a steadier alarm the lower the hull. */
    public static float warnGap(float hullFrac) {
        return 0.55f + 2.4f * Math.max(0f, hullFrac);
    }

    public void update(World w, float dt, boolean active) {
        SfxBus bus = Audio.bus();
        if (!active || w.runEnded()) {
            bus.stopLoops();
            level = 0f;
            warn = 0f;
            return;
        }
        Ship s = w.ship;
        float push = (float) Math.hypot(s.thrustX, s.thrustY);
        float speed = (float) Math.hypot(s.vx, s.vy) / Math.max(1f, s.maxSpeed());
        float target = engineTarget(push, speed, s.boosting);
        level += (target - level) * (1f - (float) Math.exp(-6f * dt));
        bus.engine(level, s.boosting);

        float frac = s.hp / Math.max(1f, s.maxHp());
        if (frac < 0.25f && s.hp > 0) {
            warn -= dt;
            if (warn <= 0) {
                bus.play(Sfx.LOW_HULL, 1f, 0f);
                warn = warnGap(frac);
            }
        } else {
            warn = 0f;
        }
    }
}
