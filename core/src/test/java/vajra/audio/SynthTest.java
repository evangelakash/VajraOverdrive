package vajra.audio;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** EVERY SOUND IS A SOUND. No one can listen to a test, so these are the things a bad synth gets
 *  wrong: silence, clipping, NaN, a click at the loop, a sound that is secretly another sound, a
 *  coin as dull as a thump. They run the whole catalogue. */
class SynthTest {
    private static double rms(short[] p) {
        double s = 0;
        for (short v : p) s += (v / 32768.0) * (v / 32768.0);
        return Math.sqrt(s / p.length);
    }

    private static int peak(short[] p) {
        int m = 0;
        for (short v : p) m = Math.max(m, Math.abs((int) v));
        return m;
    }

    /** A cheap brightness: the share of the signal that is high-frequency, by first differences
     *  (the more a signal wiggles from sample to sample, the brighter it sounds). */
    private static double brightness(short[] p) {
        double d = 0, e = 1e-9;
        for (int i = 1; i < p.length; i++) {
            double x = p[i] / 32768.0, y = p[i - 1] / 32768.0;
            d += (x - y) * (x - y);
            e += x * x;
        }
        return d / e;
    }

    @Test
    void everySoundIsAudibleFiniteAndNeverClipped() {
        for (Sfx s : Sfx.values()) {
            short[] p = Synth.render(s);
            assertTrue(p.length > Synth.RATE / 40, s + " is long enough to hear: " + p.length);
            assertTrue(rms(p) > 0.02, s + " is audible: rms " + rms(p));
            int pk = peak(p);
            assertTrue(pk <= 32767, s + " fits 16 bits");
            assertTrue(pk > 20000, s + " is normalised up to a healthy level: " + pk);
            assertTrue(pk < 31500, s + " leaves headroom, so it never clips: " + pk);
        }
    }

    @Test
    void everySoundHasASensibleLength() {
        for (Sfx s : Sfx.values()) {
            double sec = Synth.length(s) / (double) Synth.RATE;
            assertTrue(sec >= 0.04 && sec <= 3.6, s + " lasts " + sec + " s");
        }
        // a gun is a blip, a fanfare a phrase: the lengths say what kind of sound it is
        assertTrue(Synth.length(Sfx.SHOT_MG) < Synth.length(Sfx.SHOT_SHELL));
        assertTrue(Synth.length(Sfx.SHOT_SHELL) < Synth.length(Sfx.BOSS_DEATH));
        assertTrue(Synth.length(Sfx.COIN) < Synth.length(Sfx.PIECE));
        assertTrue(Synth.length(Sfx.UI_MOVE) < Synth.length(Sfx.SHOP_OPEN));
    }

    @Test
    void theSameSoundIsTheSameBytesEveryTime() {
        for (Sfx s : Sfx.values()) assertArrayEquals(Synth.render(s), Synth.render(s), s.name());
    }

    @Test
    void noTwoSoundsAreSecretlyTheSame() {
        Set<Integer> seen = new HashSet<>();
        for (Sfx s : Sfx.values()) {
            int h = java.util.Arrays.hashCode(Synth.render(s));
            assertTrue(seen.add(h), s + " is identical to another sound");
        }
    }

    /** Every sound starts and ends near silence, so it never clicks in or out (the loop excepted,
     *  which is tested for its seam instead). */
    @Test
    void soundsFadeInAndOutWithoutAClick() {
        for (Sfx s : Sfx.values()) {
            if (s == Sfx.THRUST) continue;
            short[] p = Synth.render(s);
            assertTrue(Math.abs(p[0]) < 1500, s + " starts at silence: " + p[0]);
            assertTrue(Math.abs(p[p.length - 1]) < 1500, s + " ends at silence: " + p[p.length - 1]);
        }
    }

    /** The engine loop plays end to end forever: the step across the seam must be no bigger than
     *  an ordinary step inside it. */
    @Test
    void theEngineLoopIsSeamless() {
        short[] p = Synth.render(Sfx.THRUST);
        assertEquals(Synth.RATE, p.length, "one whole second");
        double maxStep = 0;
        for (int i = 1; i < p.length; i++) maxStep = Math.max(maxStep, Math.abs(p[i] - p[i - 1]));
        double seam = Math.abs(p[0] - p[p.length - 1]);
        assertTrue(seam <= maxStep * 1.5 + 200, "the seam (" + seam + ") is no bigger than the loop's own steps (" + maxStep + ")");
    }

    /** The engine is a low rumble, so it must not be bright. */
    @Test
    void characterMatchesTheEvent() {
        // (a first-difference measure scales with the sample rate; an explosion's crackle is bright
        // noise, so the coin's margin over it is modest, the shard's is wide)
        assertTrue(brightness(Synth.render(Sfx.COIN)) > brightness(Synth.render(Sfx.EXPLODE_L)) * 1.8,
                "a coin is brighter than a big explosion");
        assertTrue(brightness(Synth.render(Sfx.SHARD)) > brightness(Synth.render(Sfx.EXPLODE_L)) * 3,
                "a shard sparkles, a big explosion rumbles");
        assertTrue(brightness(Synth.render(Sfx.THRUST)) < brightness(Synth.render(Sfx.COIN)) / 3,
                "the engine is a rumble, not a ping");
        assertTrue(brightness(Synth.render(Sfx.SHOT_MG)) > brightness(Synth.render(Sfx.SHOT_BOMB)),
                "a machine gun is sharper than a bomb");
    }

    /** A boom is loud where it starts and dies away; a rising charge is loudest at its end. */
    @Test
    void envelopesFollowTheirStory() {
        double bangEarly = rms(slice(Synth.render(Sfx.EXPLODE_M), 0.0, 0.12));
        double bangLate = rms(slice(Synth.render(Sfx.EXPLODE_M), 0.45, 0.57));
        assertTrue(bangEarly > bangLate * 4, "an explosion dies away");
        double windEarly = rms(slice(Synth.render(Sfx.ASTRA_WIND), 0.0, 0.5));
        double windLate = rms(slice(Synth.render(Sfx.ASTRA_WIND), 1.3, 1.8));
        assertTrue(windLate > windEarly * 3, "the Astra gathers: it is loudest as it nears release");
        double victoryOpening = rms(slice(Synth.render(Sfx.VICTORY), 0.0, 0.5));
        assertTrue(victoryOpening < 0.02, "the fanfare opens after a beat of silence");
    }

    private static short[] slice(short[] p, double from, double to) {
        int a = (int) (from * Synth.RATE), b = Math.min(p.length, (int) (to * Synth.RATE));
        return java.util.Arrays.copyOfRange(p, a, b);
    }

    @Test
    void theWavFileIsWhole() {
        short[] p = Synth.render(Sfx.COIN);
        byte[] w = Wav.encode(p, Synth.RATE);
        assertEquals(44 + p.length * 2, w.length);
        assertEquals("RIFF", new String(w, 0, 4));
        assertEquals("WAVE", new String(w, 8, 4));
        assertEquals("data", new String(w, 36, 4));
        assertEquals(p.length * 2, le32(w, 40), "the data chunk's length");
        assertEquals(36 + p.length * 2, le32(w, 4), "the RIFF length");
        assertEquals(Synth.RATE, le32(w, 24), "the sample rate");
        assertEquals(1, w[22], "mono");
        assertEquals(16, w[34], "16 bits");
        for (int i = 0; i < p.length; i += 97)
            assertEquals(p[i], (short) ((w[44 + i * 2] & 0xFF) | (w[45 + i * 2] << 8)), "sample " + i);
    }

    private static int le32(byte[] b, int at) {
        return (b[at] & 0xFF) | ((b[at + 1] & 0xFF) << 8) | ((b[at + 2] & 0xFF) << 16) | ((b[at + 3] & 0xFF) << 24);
    }
}
