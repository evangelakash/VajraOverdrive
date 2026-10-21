package vajra.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Random;

/** The sound card's end of the {@link SfxBus}. At start-up it makes every sound (a file named
 *  {@code sfx/<name>.ogg} or {@code .wav} in the assets wins over the synthesised one), writes the
 *  synthesised ones to a temporary folder libGDX can read, and from then on plays them:
 *
 *  <ul>
 *   <li><b>One report per gap.</b> A sound is not played again inside its {@link Sfx#minGapMs}: a
 *       five-pellet volley is one shot, a shower of coins a patter.</li>
 *   <li><b>A little life.</b> Shots and hits vary their pitch by a few percent so repeats do not
 *       machine-gun; menus and bells do not, they are instruments.</li>
 *   <li><b>Coins climb.</b> Coins taken in a run rise a whole tone each, up to seven, and the run
 *       resets after a pause: the old arcade reward, with no state in the world.</li>
 *   <li><b>The engine</b> is one looped sound whose volume and pitch follow how hard the ship
 *       works.</li>
 *  </ul>
 *
 *  <p>With no audio device (a headless test, a machine with none) it does nothing, quietly. */
public final class SfxPlayer implements SfxBus {
    private final EnumMap<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);
    private final long[] lastMs = new long[Sfx.values().length];
    private final Random jitter = new Random();
    private int coinStep;
    private long coinMs;
    private long engineId = -1;

    public SfxPlayer() {
        if (Gdx.audio == null) return;
        Path dir = null;
        try {
            dir = Files.createTempDirectory("vajra-sfx-");
            final Path d = dir;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> wipe(d)));
        } catch (Exception e) {
            System.err.println("sound: no temp folder, effects off (" + e + ")");
            return;
        }
        for (Sfx s : Sfx.values()) {
            try {
                FileHandle f = override(s);
                if (f == null) {
                    Path p = dir.resolve(s.name().toLowerCase() + ".wav");
                    Files.write(p, Wav.encode(Synth.render(s), Synth.RATE));
                    f = Gdx.files.absolute(p.toString());
                }
                sounds.put(s, Gdx.audio.newSound(f));
            } catch (Exception | Error e) {
                System.err.println("sound: " + s + " did not load (" + e + ")");
            }
        }
    }

    private static FileHandle override(Sfx s) {
        for (String ext : new String[] { ".ogg", ".wav" }) {
            FileHandle f = Gdx.files.internal("sfx/" + s.name().toLowerCase() + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    private static void wipe(Path d) {
        try (java.util.stream.Stream<Path> w = Files.walk(d)) {
            w.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
            // a temp folder left behind is the OS's to clean
        }
    }

    /** The pilot's volume setting, 0..1. */
    private static float master() {
        try {
            return vajra.profile.Profiles.active().settings.sfxVolume;
        } catch (RuntimeException e) {
            return 0.8f;
        }
    }

    /** Is a play allowed `sinceMs` after the last one, given the sound's minimum gap? */
    static boolean gapOpen(long sinceMs, int minGapMs) {
        return sinceMs >= minGapMs;
    }

    /** The pitch of the n-th coin in a run: a whole tone (2 semitones) per coin, up to seven. */
    static float coinPitch(int step) {
        return (float) Math.pow(2.0, Math.min(7, Math.max(0, step)) * 2 / 12.0);
    }

    /** How much a sound's pitch may wander either side, as a fraction. */
    static float wander(Sfx s) {
        switch (s) {
            case SHOT_MAGNUM: case SHOT_RIFLE: case SHOT_MG: case SHOT_SHELL: case SHOT_SCATTER: case SHOT_SNIPER:
            case SHOT_MISSILE: case ENEMY_SHOT: case HIT_ENEMY: case IMPACT: case EXPLODE_S: case EXPLODE_M:
            case HULL_HIT: case DROP:
                return s == Sfx.SHOT_MAGNUM || s == Sfx.SHOT_SHELL || s == Sfx.SHOT_SNIPER ? 0.03f : 0.05f;
            default:
                return 0f;
        }
    }

    @Override
    public void play(Sfx s, float volume, float pan) {
        Sound snd = sounds.get(s);
        if (snd == null) return;
        long now = System.nanoTime() / 1_000_000L;
        if (!gapOpen(now - lastMs[s.ordinal()], s.minGapMs)) return;
        lastMs[s.ordinal()] = now;
        float vol = s.gain * Math.max(0f, Math.min(1f, volume)) * master();
        if (vol <= 0.003f) return;
        float pitch = 1f;
        if (s == Sfx.COIN) {
            coinStep = now - coinMs < 450 ? Math.min(coinStep + 1, 7) : 0;
            coinMs = now;
            pitch = coinPitch(coinStep);
        } else if (wander(s) > 0) {
            pitch = 1f + (jitter.nextFloat() - 0.5f) * 2f * wander(s);
        }
        snd.play(vol, pitch, pan);
    }

    @Override
    public void engine(float level, boolean boosting) {
        Sound snd = sounds.get(Sfx.THRUST);
        if (snd == null) return;
        level = Math.max(0f, Math.min(1f, level));
        if (engineId < 0) {
            if (level < 0.02f) return;
            engineId = snd.loop(0f);
        }
        snd.setVolume(engineId, Sfx.THRUST.gain * master() * (float) Math.pow(level, 0.8));
        snd.setPitch(engineId, 0.75f + 0.7f * level + (boosting ? 0.3f : 0f));
    }

    @Override
    public void stopLoops() {
        Sound snd = sounds.get(Sfx.THRUST);
        if (snd != null && engineId >= 0) snd.stop(engineId);
        engineId = -1;
    }
}
