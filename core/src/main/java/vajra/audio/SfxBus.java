package vajra.audio;

/** Where a sound goes. The simulation and the screens speak to this and never to the sound card,
 *  so the world stays headless: in a test the bus is a recorder (or silence), in the game it is
 *  the {@link SfxPlayer}. */
public interface SfxBus {
    /** Play `s` at `volume` (0..1, on top of its own gain) panned `pan` (-1 left .. 1 right). */
    void play(Sfx s, float volume, float pan);

    /** The engine's loop: `level` 0..1 is how hard it is working, `boosting` lifts its pitch. */
    default void engine(float level, boolean boosting) { }

    /** Hush every loop (a menu opened, the run ended). */
    default void stopLoops() { }

    SfxBus NONE = (s, v, p) -> { };
}
