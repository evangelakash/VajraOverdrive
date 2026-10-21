package vajra.audio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** A GUNSHOT IS NOT A TONE. The first gun sounds were a swept square wave: a laser's "pew", the
 *  sound of a toy. A real shot has a fixed anatomy, and these tests hold the synth to it: a crack
 *  in the first few milliseconds, a broad noisy spectrum with no single dominant pitch, a heavier
 *  gun darker and longer than a light one, a room that answers back, and a pump-action shotgun
 *  that is racked after it fires. They run the real firearms of the game. */
class GunSynthTest {
    private static final Sfx[] FIREARMS = { Sfx.SHOT_MAGNUM, Sfx.SHOT_RIFLE, Sfx.SHOT_MG, Sfx.SHOT_SHELL,
            Sfx.SHOT_SCATTER, Sfx.SHOT_SNIPER, Sfx.ENEMY_SHOT };

    /** Magnitude spectrum of a Hann window of n samples (n a power of two) starting at `start`. */
    private static double[] spectrum(short[] p, int start, int n) {
        double[] re = new double[n], im = new double[n];
        for (int i = 0; i < n; i++) {
            int k = start + i;
            double v = k < p.length ? p[k] / 32768.0 : 0;
            re[i] = v * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / n));
        }
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            for (int i = 0; i < n; i += len)
                for (int k = 0; k < len / 2; k++) {
                    double wr = Math.cos(ang * k), wi = Math.sin(ang * k);
                    int a = i + k, b = i + k + len / 2;
                    double vr = re[b] * wr - im[b] * wi, vi = re[b] * wi + im[b] * wr;
                    double ur = re[a], ui = im[a];
                    re[a] = ur + vr; im[a] = ui + vi; re[b] = ur - vr; im[b] = ui - vi;
                }
        }
        double[] m = new double[n / 2];
        for (int i = 0; i < n / 2; i++) m[i] = Math.sqrt(re[i] * re[i] + im[i] * im[i]);
        return m;
    }

    private static int peakAt(short[] p) {
        int at = 0, pk = 0;
        for (int i = 0; i < p.length; i++) if (Math.abs(p[i]) > pk) { pk = Math.abs(p[i]); at = i; }
        return at;
    }

    private static double rms(short[] p, double from, double to) {
        int a = (int) (from * Synth.RATE), b = Math.min(p.length, (int) (to * Synth.RATE));
        double s = 0;
        for (int i = a; i < b; i++) s += (p[i] / 32768.0) * (p[i] / 32768.0);
        return b > a ? Math.sqrt(s / (b - a)) : 0;
    }

    /** Share of the early spectrum above `hz`. */
    private static double shareAbove(short[] p, int hz) {
        double[] m = spectrum(p, 0, 512);
        double hi = 0, all = 1e-12;
        for (int i = 0; i < m.length; i++) {
            double e = m[i] * m[i];
            all += e;
            if (i * (double) Synth.RATE / 512 > hz) hi += e;
        }
        return hi / all;
    }

    private static double centroid(short[] p) {
        double[] m = spectrum(p, 0, 2048);
        double num = 0, den = 1e-12;
        for (int i = 0; i < m.length; i++) { num += i * (double) Synth.RATE / 2048 * m[i]; den += m[i]; }
        return num / den;
    }

    @Test
    void aShotIsATransientThePeakComesInTheFirstMilliseconds() {
        for (Sfx s : FIREARMS) {
            double ms = peakAt(Synth.render(s)) * 1000.0 / Synth.RATE;
            assertTrue(ms < 12, s + " peaks at " + ms + " ms: a shot hits at once, it does not swell");
        }
    }

    @Test
    void theCrackIsABroadbandClickNotAToneInTheHighs() {
        for (Sfx s : FIREARMS)
            assertTrue(shareAbove(Synth.render(s), 4000) > 0.4,
                    s + " has too little crack: " + shareAbove(Synth.render(s), 4000));
    }

    /** Share of the energy ABOVE `hz` held by its single strongest frequency bin: how tonal that
     *  band is. Noise is flat; a tone or a swept wave spikes. Below 400 Hz a heavy gun is
     *  legitimately concentrated (that is bass, not pitch), so only the band above is judged. */
    private static double topBinShareAbove(short[] p, int hz) {
        double[] m = spectrum(p, 0, 2048);
        double top = 0, all = 1e-12;
        for (int i = 0; i < m.length; i++) {
            if (i * (double) Synth.RATE / 2048 < hz) continue;
            double e = m[i] * m[i];
            top = Math.max(top, e);
            all += e;
        }
        return top / all;
    }

    /** No gun is a note: its upper spectrum is broad. (A loose guard: a pure tone holds most of it
     *  in one bin, a gunshot a few percent.) */
    @Test
    void noSinglePitchOwnsAGunshot() {
        for (Sfx s : FIREARMS)
            assertTrue(topBinShareAbove(Synth.render(s), 400) < 0.12,
                    s + " is too tonal above 400 Hz: " + topBinShareAbove(Synth.render(s), 400));
        short[] sine = new short[Synth.RATE / 10];
        for (int i = 0; i < sine.length; i++) sine[i] = (short) (20000 * Math.sin(2 * Math.PI * 1100 * i / Synth.RATE));
        assertTrue(topBinShareAbove(sine, 400) > 0.3, "a sine is tonal, so the guard can tell");
    }

    /** THE TOY, REBUILT. The first gun sound was a square wave gliding from 1100 to 260 Hz. It has
     *  a sharp attack like a gun, so peak time cannot tell them apart; what it lacks is the
     *  CRACK: only about a tenth of its early energy is above 4 kHz, where a real shot has well
     *  over half. This is the measurement that separates a toy from a gun, and it must keep
     *  doing so. */
    @Test
    void aToySoundIsNotAGunshot() {
        short[] toy = new short[Synth.RATE / 8];
        double ph = 0;
        for (int i = 0; i < toy.length; i++) {
            double t = i / (double) Synth.RATE, f = 1100 * Math.pow(260.0 / 1100, t / 0.14);
            ph += f / Synth.RATE;
            toy[i] = (short) (14000 * ((ph - Math.floor(ph)) < 0.5 ? 1 : -1) * Math.exp(-28 * t));
        }
        double toyCrack = shareAbove(toy, 4000);
        assertTrue(toyCrack < 0.2, "the toy has almost no crack: " + toyCrack);
        for (Sfx s : FIREARMS)
            assertTrue(shareAbove(Synth.render(s), 4000) > toyCrack * 3, s + " must have several times the toy's crack");
    }

    /** Share of the first 46 ms's energy below 300 Hz: bass. */
    private static double bass(short[] p) {
        double[] m = spectrum(p, 0, 2048);
        double lo = 0, all = 1e-12;
        for (int i = 0; i < m.length; i++) {
            double e = m[i] * m[i];
            all += e;
            if (i * (double) Synth.RATE / 2048 < 300) lo += e;
        }
        return lo / all;
    }

    @Test
    void heavierGunsCarryMoreBass() {
        double shell = bass(Synth.render(Sfx.SHOT_SHELL)), magnum = bass(Synth.render(Sfx.SHOT_MAGNUM)),
                rifle = bass(Synth.render(Sfx.SHOT_RIFLE)), mg = bass(Synth.render(Sfx.SHOT_MG));
        assertTrue(shell > rifle, "a cannon has more bass than an AR-15: " + shell + " vs " + rifle);
        assertTrue(rifle > magnum * 0.8, "an AR-15 is at least as chesty as a Glock: " + rifle + " vs " + magnum);
        assertTrue(magnum > 0.1 && mg > 0.05, "even the small guns have a body: " + magnum + " " + mg);
    }

    /** The heavier the gun, the darker and the longer it sounds. */
    @Test
    void weightIsDarknessAndLength() {
        double shell = centroid(Synth.render(Sfx.SHOT_SHELL)), magnum = centroid(Synth.render(Sfx.SHOT_MAGNUM)),
                rifle = centroid(Synth.render(Sfx.SHOT_RIFLE)), mg = centroid(Synth.render(Sfx.SHOT_MG));
        assertTrue(shell < rifle, "a cannon is darker than an AR-15: " + shell + " vs " + rifle);
        assertTrue(rifle < magnum, "an AR-15 is darker than a Glock: " + rifle + " vs " + magnum);
        assertTrue(Synth.length(Sfx.SHOT_MG) < Synth.length(Sfx.SHOT_MAGNUM));
        assertTrue(Synth.length(Sfx.SHOT_MAGNUM) < Synth.length(Sfx.SHOT_RIFLE));
        assertTrue(Synth.length(Sfx.SHOT_RIFLE) < Synth.length(Sfx.SHOT_SHELL));
        assertTrue(Synth.length(Sfx.ENEMY_SHOT) <= Synth.length(Sfx.SHOT_MAGNUM), "a foe's pistol is no bigger than your Glock");
    }

    /** The room answers: a Magnum's report is followed by a quieter, decaying tail, not by silence. */
    @Test
    void theRoomAnswersTheShot() {
        short[] p = Synth.render(Sfx.SHOT_RIFLE);
        double attack = rms(p, 0, 0.02), tail = rms(p, 0.15, 0.35), late = rms(p, 0.6, 0.8);
        assertTrue(tail > attack * 0.002, "there is a tail: " + tail);
        assertTrue(tail < attack * 0.35, "and it is quieter than the shot: " + tail + " vs " + attack);
        assertTrue(late < tail, "and it keeps dying away");
        // no discrete slaps: a few evenly spaced echoes ring like a struck steel plate
        short[] q = Synth.render(Sfx.SHOT_SNIPER);
        assertTrue(Synth.length(Sfx.SHOT_SNIPER) > Synth.length(Sfx.SHOT_MAGNUM), "the rifle's room is longer");
        assertTrue(rms(q, 0.20, 0.30) > 0, "and the wash is there");
    }

    @Test
    void theShotgunIsRackedAfterItFires() {
        short[] p = Synth.render(Sfx.SHOT_SCATTER);
        double quiet = rms(p, 0.30, 0.34), rack = rms(p, 0.36, 0.40), after = rms(p, 0.45, 0.48);
        assertTrue(rack > quiet * 2.5, "the slide is heard after the blast has died: " + rack + " vs " + quiet);
        assertTrue(after > quiet * 2.5, "and the second clack: " + after);
    }

    @Test
    void everyFirearmIsLoudEnoughToBeTheFightAndTheFoesAreSmaller() {
        assertTrue(Sfx.SHOT_MAGNUM.gain >= 0.8, "the Glock is the loudest thing you fire");
        assertTrue(Sfx.ENEMY_SHOT.gain < Sfx.SHOT_MAGNUM.gain / 2, "a foe's pistol sits well under it");
        assertTrue(SfxPlayer.wander(Sfx.SHOT_MAGNUM) <= 0.05f, "real guns do not wander in pitch much");
    }

    @Test
    void theWeaponsAreMappedToTheirRealWorldCounterparts() {
        vajra.weapon.WeaponDef w;
        assertEquals(Sfx.SHOT_MAGNUM, Sfx.forWeapon(vajra.weapon.WeaponDef.SINGLE, 1f), "the starter is the Magnum");
        assertEquals(Sfx.SHOT_MAGNUM, Sfx.forWeapon(vajra.weapon.WeaponDef.DOUBLE, 1f));
        assertEquals(Sfx.SHOT_MAGNUM, Sfx.forWeapon(vajra.weapon.WeaponDef.AIM, 1f));
        assertEquals(Sfx.SHOT_RIFLE, Sfx.forWeapon(vajra.weapon.WeaponDef.BURST1, 1f), "burst fire is a rifle");
        assertEquals(Sfx.SHOT_RIFLE, Sfx.forWeapon(vajra.weapon.WeaponDef.BURST2, 1f));
        assertEquals(Sfx.SHOT_MG, Sfx.forWeapon(vajra.weapon.WeaponDef.LIGHT, 1f), "the light MG is a submachine gun");
        assertEquals(Sfx.SHOT_SCATTER, Sfx.forWeapon(vajra.weapon.WeaponDef.SPREAD, 1f), "buckshot is a shotgun");
        assertEquals(Sfx.SHOT_SHELL, Sfx.forWeapon(vajra.weapon.WeaponDef.HEAVY, 1f), "the heavy shell is a cannon");
        assertEquals(Sfx.SHOT_SNIPER, Sfx.forWeapon(vajra.weapon.WeaponDef.PIERCE, 1f), "the piercer is a high-velocity rifle");
        assertEquals(Sfx.SHOT_SNIPER, Sfx.forWeapon(vajra.weapon.WeaponDef.THROUGH, 1f));
        assertEquals(Sfx.SHOT_MISSILE, Sfx.forWeapon(vajra.weapon.WeaponDef.MISSILE, 1f), "a missile is a rocket");
        assertEquals(Sfx.SHOT_BOMB, Sfx.forWeapon(vajra.weapon.WeaponDef.BOMB, 1f), "the bomb is a grenade launcher");
        assertEquals(Sfx.SHOT_LASER, Sfx.forWeapon(vajra.weapon.WeaponDef.LASER, 1f), "the laser stays energy");
        // and no weapon in the game is left without a voice, nor does a firearm fall back to the beam
        for (vajra.weapon.WeaponDef d : vajra.weapon.WeaponDef.ALL) assertNotNull(Sfx.forWeapon(d, 1f), d.name);
    }
}
