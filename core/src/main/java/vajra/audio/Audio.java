package vajra.audio;

/** The one door to sound. A static, in the house's style (Net, Profiles): the player installs a
 *  real bus once at start-up; until then, and in every headless test, sound is silence. */
public final class Audio {
    private static volatile SfxBus bus = SfxBus.NONE;

    private Audio() { }

    public static void use(SfxBus b) {
        bus = b == null ? SfxBus.NONE : b;
    }

    public static SfxBus bus() {
        return bus;
    }

    /** A sound with no place in the world: a menu, the hangar, the ship's own voice. */
    public static void ui(Sfx s) {
        bus.play(s, 1f, 0f);
    }

    /** A sound in the world, heard from `listener`: quieter with distance, panned by where it is. */
    public static void at(Sfx s, float x, float y, float listenerX, float listenerY) {
        float dx = x - listenerX, dy = y - listenerY;
        float vol = volumeAt(dx, dy);
        if (vol <= 0.04f) return;
        bus.play(s, vol, panAt(dx));
    }

    /** How far a sound carries, in world px. About a screen and a half. */
    public static final float EARSHOT = 1100f, PAN_SPAN = 720f;

    /** Distance falloff: 1 at the ship, 0 at earshot, curved so near things stay present. */
    public static float volumeAt(float dx, float dy) {
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d >= EARSHOT) return 0f;
        float k = 1f - d / EARSHOT;
        return k * (2f - k); // an ease-out: loud nearby, fading late
    }

    /** Left-right placement, softened so a sound at the edge is not a hard single-ear click. */
    public static float panAt(float dx) {
        return Math.max(-1f, Math.min(1f, dx / PAN_SPAN)) * 0.75f;
    }
}
