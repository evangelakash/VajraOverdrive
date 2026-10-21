package vajra.audio;

/** THE FORGE OF SOUNDS — every effect in the game, made from nothing but arithmetic.
 *
 *  <p>No sound file is downloaded or stored. Each {@link Sfx} has a recipe here, built from a
 *  handful of parts: a swept oscillator (sine, triangle, square, saw), a shaped burst of noise
 *  through a sweeping filter, and a bell (a note with the inharmonic overtones of struck metal,
 *  the high ones dying first). Layered and timed, those make a coin's two pings, a shell's thump,
 *  a missile's rising rush, the gong when the hangar opens.
 *
 *  <p>It is deterministic (the noise is a seeded generator), mono, 22 050 Hz, 16-bit, so the same
 *  sound is the same bytes every run, and a test can check every one of them: audible, never
 *  clipped, the right length, the loop seamless. Pure maths with no GL and no sound card, so it
 *  runs headless. */
public final class Synth {
    public static final int RATE = 22050;

    private Synth() { }

    private enum W { SINE, TRI, SQUARE, SAW }

    // =============================================================== rendering
    /** The finished sound: normalised, softly limited, faded at both ends so it never clicks. */
    public static short[] render(Sfx s) {
        float[] f = mix(s);
        float peak = 1e-6f;
        for (int i = 0; i < f.length; i++) {
            f[i] = (float) Math.tanh(f[i] * 1.15); // a soft ceiling, not a hard clip
            peak = Math.max(peak, Math.abs(f[i]));
        }
        float k = 0.92f / peak;
        int fade = (int) (0.004f * RATE);
        short[] out = new short[f.length];
        for (int i = 0; i < f.length; i++) {
            float edge = s == Sfx.THRUST ? 1f : Math.min(1f, Math.min(i / (float) fade, (f.length - 1 - i) / (float) fade));
            out[i] = (short) Math.round(f[i] * k * edge * 32767f);
        }
        return out;
    }

    /** How long a sound runs, in samples (for tests and for the player's loop bookkeeping). */
    public static int length(Sfx s) {
        return mix(s).length;
    }

    /** The raw mix before normalising: a unit-ish float signal. */
    static float[] mix(Sfx s) {
        switch (s) {
            // ---- the menus and the bazaar ------------------------------------------------------
            case UI_MOVE: {
                float[] o = buf(0.09f);
                tone(o, 0, 0.08f, W.TRI, 1250, 900, 0.8f, 0.002f, 55, 0, 0);
                noise(o, 0, 0.012f, 0.25f, 0.0005f, 120, 1, 'h', 3000, 3000);
                return o;
            }
            case UI_SELECT: {
                float[] o = buf(0.34f);
                bell(o, 0f, 660, 0.8f, 0.30f, 14);
                bell(o, 0.07f, 990, 0.8f, 0.27f, 13);
                return o;
            }
            case UI_BACK: {
                float[] o = buf(0.26f);
                bell(o, 0f, 880, 0.7f, 0.2f, 16);
                bell(o, 0.06f, 587, 0.7f, 0.2f, 15);
                return o;
            }
            case UI_TAB: {
                float[] o = buf(0.14f);
                noise(o, 0, 0.1f, 0.7f, 0.004f, 28, 2, 'b', 2400, 1300);
                tone(o, 0, 0.07f, W.TRI, 700, 520, 0.45f, 0.002f, 40, 0, 0);
                return o;
            }
            case UI_DENY: {
                float[] o = buf(0.26f);
                for (int i = 0; i < 2; i++) {
                    tone(o, i * 0.11f, 0.10f, W.SQUARE, 120, 105, 0.45f, 0.002f, 16, 0, 0);
                    tone(o, i * 0.11f, 0.10f, W.SQUARE, 127, 111, 0.35f, 0.002f, 16, 0, 0);
                }
                return o;
            }
            case UI_CONFIRM: {
                float[] o = buf(0.40f);
                bell(o, 0f, 1175, 0.9f, 0.38f, 10);
                return o;
            }
            case PURCHASE: {
                float[] o = buf(0.95f);
                float[] ping = { 1319, 1568, 1760, 2093, 2349 };
                for (int i = 0; i < ping.length; i++) bell(o, i * 0.065f, ping[i], 0.5f, 0.3f, 14);
                float t = ping.length * 0.065f + 0.02f;
                bell(o, t, 1046, 0.6f, 0.55f, 5);
                bell(o, t, 1319, 0.5f, 0.55f, 5);
                bell(o, t, 1568, 0.45f, 0.55f, 5);
                return o;
            }
            case SELL: {
                float[] o = buf(0.55f);
                float[] ping = { 2093, 1760, 1319 };
                for (int i = 0; i < ping.length; i++) bell(o, i * 0.07f, ping[i], 0.55f, 0.3f, 14);
                tone(o, 0, 0.2f, W.SINE, 330, 220, 0.25f, 0.01f, 12, 0, 0);
                return o;
            }
            case SHOP_OPEN: {
                float[] o = buf(1.5f);
                bell(o, 0f, 196, 0.9f, 1.4f, 3);
                bell(o, 0.10f, 784, 0.5f, 1.0f, 5);
                bell(o, 0.22f, 1175, 0.4f, 0.9f, 6);
                return o;
            }
            case SHOP_CLOSE: {
                float[] o = buf(0.7f);
                bell(o, 0f, 392, 0.7f, 0.5f, 7);
                bell(o, 0.10f, 262, 0.8f, 0.55f, 6);
                return o;
            }
            case DOCK: {
                float[] o = buf(0.75f);
                tone(o, 0, 0.7f, W.SINE, 110, 220, 0.6f, 0.06f, 2.2f, 12, 0.03f);
                noise(o, 0, 0.6f, 0.25f, 0.1f, 3, 3, 'l', 320, 320);
                return o;
            }

            // ---- the guns ---------------------------------------------------------------------
            case SHOT_BOLT: {
                float[] o = buf(0.16f);
                tone(o, 0, 0.14f, W.SQUARE, 1100, 260, 0.5f, 0.001f, 28, 0, 0);
                tone(o, 0, 0.10f, W.SAW, 2200, 600, 0.2f, 0.001f, 45, 0, 0);
                noise(o, 0, 0.01f, 0.3f, 0.0005f, 150, 4, 'h', 4000, 4000);
                return o;
            }
            case SHOT_MG: {
                float[] o = buf(0.06f);
                noise(o, 0, 0.04f, 0.8f, 0.001f, 80, 5, 'b', 1800, 1200);
                tone(o, 0, 0.035f, W.TRI, 1400, 900, 0.4f, 0.001f, 90, 0, 0);
                return o;
            }
            case SHOT_SHELL: {
                float[] o = buf(0.34f);
                tone(o, 0, 0.3f, W.SINE, 160, 48, 1f, 0.002f, 14, 0, 0);
                noise(o, 0, 0.24f, 0.8f, 0.002f, 16, 6, 'l', 900, 200);
                tone(o, 0, 0.2f, W.SAW, 300, 80, 0.25f, 0.002f, 20, 0, 0);
                return o;
            }
            case SHOT_SCATTER: {
                float[] o = buf(0.26f);
                noise(o, 0, 0.2f, 0.9f, 0.002f, 20, 7, 'b', 2800, 1200);
                tone(o, 0, 0.2f, W.SINE, 140, 70, 0.6f, 0.002f, 22, 0, 0);
                noise(o, 0, 0.015f, 0.4f, 0.0005f, 100, 8, 'h', 3500, 3500);
                return o;
            }
            case SHOT_LASER: {
                float[] o = buf(0.34f);
                tone(o, 0, 0.3f, W.SAW, 2400, 380, 0.5f, 0.002f, 10, 35, 0.12f);
                tone(o, 0, 0.3f, W.SINE, 1200, 200, 0.4f, 0.002f, 12, 0, 0);
                noise(o, 0, 0.05f, 0.25f, 0.002f, 40, 9, 'h', 3000, 3000);
                return o;
            }
            case SHOT_MISSILE: {
                float[] o = buf(0.55f);
                noise(o, 0, 0.5f, 0.9f, 0.08f, 6, 10, 'b', 350, 2200);
                tone(o, 0, 0.45f, W.SINE, 180, 420, 0.25f, 0.06f, 7, 0, 0);
                noise(o, 0, 0.012f, 0.3f, 0.0005f, 100, 11, 'h', 3500, 3500);
                return o;
            }
            case SHOT_BOMB: {
                float[] o = buf(0.4f);
                tone(o, 0, 0.36f, W.SINE, 120, 40, 1f, 0.003f, 10, 0, 0);
                tone(o, 0, 0.3f, W.TRI, 240, 90, 0.4f, 0.003f, 12, 0, 0);
                noise(o, 0, 0.14f, 0.5f, 0.003f, 25, 12, 'l', 500, 500);
                return o;
            }
            case SHOT_CHARGED: {
                float[] o = buf(0.42f);
                tone(o, 0, 0.32f, W.SAW, 280, 1500, 0.45f, 0.01f, 7, 0, 0);
                tone(o, 0, 0.3f, W.SINE, 560, 3000, 0.35f, 0.01f, 8, 0, 0);
                noise(o, 0, 0.03f, 0.3f, 0.001f, 60, 13, 'h', 4000, 4000);
                return o;
            }
            case ENEMY_SHOT: {
                float[] o = buf(0.14f);
                tone(o, 0, 0.12f, W.TRI, 520, 240, 0.6f, 0.002f, 25, 0, 0);
                tone(o, 0, 0.10f, W.SQUARE, 260, 130, 0.15f, 0.002f, 30, 0, 0);
                return o;
            }

            // ---- the fight --------------------------------------------------------------------
            case HIT_ENEMY: {
                float[] o = buf(0.07f);
                noise(o, 0, 0.045f, 0.8f, 0.001f, 70, 14, 'b', 2500, 1500);
                tone(o, 0, 0.05f, W.SINE, 760, 520, 0.5f, 0.001f, 60, 0, 0);
                return o;
            }
            case EXPLODE_S: {
                float[] o = buf(0.34f);
                noise(o, 0, 0.3f, 1f, 0.002f, 11, 15, 'l', 2200, 300);
                tone(o, 0, 0.26f, W.SINE, 140, 50, 0.9f, 0.002f, 12, 0, 0);
                return o;
            }
            case EXPLODE_M: {
                float[] o = buf(0.62f);
                noise(o, 0, 0.55f, 1f, 0.002f, 7, 16, 'l', 1800, 200);
                tone(o, 0, 0.5f, W.SINE, 100, 38, 1f, 0.002f, 6, 0, 0);
                for (int i = 0; i < 3; i++) noise(o, 0.05f + i * 0.07f, 0.04f, 0.3f, 0.001f, 60, 17 + i, 'h', 3000, 3000);
                return o;
            }
            case EXPLODE_L: {
                float[] o = buf(1.05f);
                noise(o, 0, 0.95f, 1f, 0.003f, 4, 20, 'l', 1400, 120);
                tone(o, 0, 0.9f, W.SINE, 75, 28, 1.1f, 0.003f, 3.5f, 0, 0);
                tone(o, 0, 0.7f, W.SAW, 90, 35, 0.3f, 0.003f, 5, 0, 0);
                for (int i = 0; i < 5; i++) noise(o, 0.06f + i * 0.09f, 0.05f, 0.3f, 0.001f, 50, 21 + i, 'h', 2500, 2500);
                return o;
            }
            case BOSS_AWAKEN: {
                float[] o = buf(2.0f);
                tone(o, 0, 1.8f, W.SAW, 62, 48, 0.7f, 0.3f, 1.2f, 9, 0.25f);
                tone(o, 0, 1.6f, W.SQUARE, 93, 70, 0.3f, 0.3f, 1.4f, 9, 0.2f);
                noise(o, 0, 1.8f, 0.4f, 0.4f, 1.5f, 30, 'l', 220, 160);
                bell(o, 0.9f, 98, 0.8f, 1.0f, 2.5f);
                return o;
            }
            case BOSS_PHASE: {
                float[] o = buf(1.0f);
                tone(o, 0, 0.9f, W.SAW, 150, 60, 0.6f, 0.005f, 3, 0, 0);
                noise(o, 0, 0.8f, 0.6f, 0.005f, 5, 31, 'l', 600, 150);
                bell(o, 0, 220, 0.5f, 0.9f, 4);
                return o;
            }
            case BOSS_DEATH: {
                float[] o = buf(2.4f);
                float[] at = { 0f, 0.28f, 0.6f };
                for (int i = 0; i < at.length; i++) {
                    noise(o, at[i], 0.9f, 1f - i * 0.15f, 0.003f, 4, 40 + i, 'l', 1400 - i * 200, 120);
                    tone(o, at[i], 0.8f, W.SINE, 80 - i * 10, 28, 1f, 0.003f, 3.5f, 0, 0);
                }
                tone(o, 0, 1.8f, W.SAW, 120, 30, 0.45f, 0.01f, 1.8f, 0, 0);
                bell(o, 0.9f, 1046, 0.5f, 1.4f, 2.5f);
                return o;
            }

            // ---- the ship ---------------------------------------------------------------------
            case HULL_HIT: {
                float[] o = buf(0.34f);
                tone(o, 0, 0.3f, W.SINE, 190, 60, 1f, 0.002f, 13, 0, 0);
                noise(o, 0, 0.22f, 0.7f, 0.002f, 18, 50, 'b', 900, 300);
                tone(o, 0, 0.2f, W.SQUARE, 95, 60, 0.35f, 0.002f, 20, 0, 0);
                return o;
            }
            case SHIELD_HIT: {
                float[] o = buf(0.34f);
                bell(o, 0, 1480, 0.9f, 0.3f, 14);
                bell(o, 0, 2200, 0.5f, 0.25f, 18);
                noise(o, 0, 0.02f, 0.3f, 0.0005f, 80, 51, 'h', 3500, 3500);
                return o;
            }
            case SHIELD_ON: {
                float[] o = buf(0.2f);
                tone(o, 0, 0.17f, W.SINE, 320, 640, 0.5f, 0.02f, 12, 0, 0);
                tone(o, 0, 0.15f, W.TRI, 640, 1280, 0.15f, 0.02f, 12, 0, 0);
                return o;
            }
            case SHIELD_OFF: {
                float[] o = buf(0.2f);
                tone(o, 0, 0.17f, W.SINE, 560, 280, 0.4f, 0.005f, 14, 0, 0);
                return o;
            }
            case SHIELD_DOWN: {
                float[] o = buf(0.55f);
                tone(o, 0, 0.5f, W.SAW, 700, 120, 0.5f, 0.004f, 6, 0, 0);
                bell(o, 0, 300, 0.4f, 0.4f, 8);
                noise(o, 0, 0.3f, 0.3f, 0.003f, 10, 52, 'b', 1500, 400);
                return o;
            }
            case DASH: {
                float[] o = buf(0.34f);
                noise(o, 0, 0.3f, 0.8f, 0.02f, 9, 53, 'b', 3400, 500);
                tone(o, 0, 0.28f, W.SINE, 500, 180, 0.35f, 0.01f, 9, 0, 0);
                return o;
            }
            case BOOST: {
                float[] o = buf(0.65f);
                noise(o, 0, 0.6f, 0.7f, 0.12f, 4.5f, 54, 'b', 500, 2600);
                tone(o, 0, 0.55f, W.SAW, 100, 320, 0.35f, 0.1f, 4, 0, 0);
                tone(o, 0, 0.5f, W.SINE, 200, 640, 0.2f, 0.1f, 4, 0, 0);
                return o;
            }
            case LOW_HULL: {
                float[] o = buf(0.3f);
                for (int i = 0; i < 2; i++) tone(o, i * 0.13f, 0.09f, W.SQUARE, 880, 880, 0.4f, 0.003f, 18, 0, 0);
                return o;
            }
            case SHIP_DESTROYED: {
                float[] o = buf(1.1f);
                noise(o, 0, 0.9f, 1f, 0.003f, 5, 55, 'l', 1800, 160);
                tone(o, 0, 0.9f, W.SINE, 220, 40, 1f, 0.003f, 4, 0, 0);
                tone(o, 0, 0.8f, W.SAW, 300, 50, 0.4f, 0.003f, 4.5f, 0, 0);
                return o;
            }
            case RESPAWN: {
                float[] o = buf(0.8f);
                tone(o, 0, 0.5f, W.SINE, 220, 880, 0.5f, 0.1f, 5, 0, 0);
                bell(o, 0.3f, 1318, 0.6f, 0.45f, 7);
                return o;
            }
            case GAME_OVER: {
                float[] o = buf(3.0f);
                bell(o, 0f, 330, 0.9f, 1.4f, 3);
                bell(o, 0.55f, 247, 0.9f, 1.5f, 3);
                bell(o, 1.1f, 185, 1f, 1.8f, 2.4f);
                tone(o, 0, 2.8f, W.SINE, 92, 80, 0.4f, 0.3f, 1.2f, 0, 0);
                return o;
            }
            case THRUST:
                return thrustLoop();

            // ---- what the world gives ---------------------------------------------------------
            case COIN: {
                float[] o = buf(0.24f);
                tone(o, 0, 0.07f, W.SINE, 1568, 1568, 0.7f, 0.001f, 25, 0, 0);
                tone(o, 0.055f, 0.17f, W.SINE, 2093, 2093, 0.8f, 0.001f, 18, 0, 0);
                tone(o, 0.055f, 0.12f, W.TRI, 4186, 4186, 0.15f, 0.001f, 25, 0, 0);
                return o;
            }
            case SHARD: {
                float[] o = buf(0.62f);
                float[] note = { 2093, 2637, 3136, 4186 };
                for (int i = 0; i < note.length; i++)
                    tone(o, i * 0.045f, 0.4f, W.SINE, note[i], note[i], 0.5f, 0.002f, 9, 7, 0.004f);
                return o;
            }
            case HEALTH: {
                float[] o = buf(0.55f);
                float[] note = { 523, 659, 784 };
                for (int i = 0; i < note.length; i++) {
                    tone(o, i * 0.1f, 0.3f, W.SINE, note[i], note[i], 0.5f, 0.03f, 6, 0, 0);
                    tone(o, i * 0.1f, 0.25f, W.TRI, note[i] * 2, note[i] * 2, 0.12f, 0.03f, 7, 0, 0);
                }
                return o;
            }
            case PIECE: {
                float[] o = buf(2.2f);
                bell(o, 0f, 131, 0.9f, 2.0f, 2);
                float[] note = { 523, 659, 784, 1046, 1319 };
                for (int i = 0; i < note.length; i++) bell(o, 0.15f + i * 0.15f, note[i], 0.5f, 1.4f, 3.5f);
                bell(o, 0.9f, 2093, 0.4f, 1.2f, 4);
                return o;
            }
            case DROP: {
                float[] o = buf(0.1f);
                tone(o, 0, 0.09f, W.SINE, 1000, 700, 0.5f, 0.001f, 45, 0, 0);
                noise(o, 0, 0.012f, 0.2f, 0.0005f, 100, 60, 'b', 2000, 2000);
                return o;
            }
            case DROP_RARE: {
                float[] o = buf(0.3f);
                tone(o, 0, 0.25f, W.SINE, 1800, 2600, 0.4f, 0.004f, 14, 0, 0);
                tone(o, 0.02f, 0.2f, W.TRI, 3600, 3600, 0.1f, 0.004f, 16, 0, 0);
                return o;
            }
            case CHECKPOINT: {
                float[] o = buf(1.4f);
                float[] note = { 262, 392, 523, 784 };
                for (int i = 0; i < note.length; i++) bell(o, i * 0.12f, note[i], 0.5f + i * 0.1f, 1.0f, 4);
                return o;
            }
            case CACHE_WAKE: {
                float[] o = buf(1.0f);
                for (int i = 0; i < 2; i++) {
                    tone(o, i * 0.35f, 0.3f, W.SAW, 82, 70, 0.6f, 0.01f, 7, 0, 0);
                    noise(o, i * 0.35f, 0.25f, 0.3f, 0.01f, 8, 61 + i, 'l', 150, 150);
                }
                return o;
            }
            case CACHE_OPEN: {
                float[] o = buf(1.0f);
                float[] note = { 523, 659, 784, 1046 };
                for (int i = 0; i < note.length; i++) bell(o, i * 0.08f, note[i], 0.6f, 0.7f, 5);
                return o;
            }
            case LIFE_UP: {
                float[] o = buf(0.95f);
                float[] note = { 784, 988, 1175, 1568 };
                for (int i = 0; i < note.length; i++) bell(o, i * 0.09f, note[i], 0.55f, 0.6f, 6);
                tone(o, 0.3f, 0.5f, W.SINE, 3136, 3136, 0.15f, 0.01f, 8, 9, 0.004f);
                return o;
            }
            case REGION: {
                float[] o = buf(1.6f);
                bell(o, 0f, 110, 0.9f, 1.4f, 2.5f);
                bell(o, 0.10f, 165, 0.6f, 1.2f, 3);
                tone(o, 0, 1.2f, W.SINE, 220, 330, 0.2f, 0.3f, 2.5f, 0, 0);
                return o;
            }
            case FORGE: {
                float[] o = buf(1.2f);
                bell(o, 0, 520, 1f, 1.0f, 6);
                noise(o, 0, 0.015f, 0.5f, 0.0005f, 70, 62, 'h', 3000, 3000);
                bell(o, 0.12f, 1046, 0.5f, 0.8f, 6);
                return o;
            }
            case ASTRA_WIND: {
                float[] o = buf(1.9f);
                tone(o, 0, 1.8f, W.SAW, 80, 900, 0.5f, 1.7f, 0.2f, 0, 0);
                noise(o, 0, 1.8f, 0.5f, 1.5f, 0.3f, 63, 'b', 300, 3500);
                tone(o, 0, 1.8f, W.SINE, 160, 1800, 0.35f, 1.6f, 0.2f, 0, 0);
                for (int i = 0; i < 3; i++) bell(o, 1.0f + i * 0.28f, 1568 * (1 + i * 0.25f), 0.25f, 0.6f, 6);
                return o;
            }
            case ASTRA_RELEASE: {
                float[] o = buf(1.5f);
                tone(o, 0, 1.3f, W.SINE, 80, 28, 1.1f, 0.003f, 3.5f, 0, 0);
                noise(o, 0, 1.2f, 1f, 0.003f, 4, 64, 'l', 3000, 150);
                tone(o, 0, 1.0f, W.SAW, 400, 60, 0.4f, 0.003f, 5, 0, 0);
                noise(o, 0.05f, 0.9f, 0.5f, 0.03f, 5, 65, 'b', 4000, 400);
                bell(o, 0.1f, 1760, 0.35f, 1.1f, 4);
                return o;
            }
            case VICTORY: {
                float[] o = buf(3.4f);
                bell(o, 0.6f, 131, 0.8f, 2.4f, 2);
                float[] note = { 523, 659, 784, 1046 };
                for (int i = 0; i < note.length; i++) bell(o, 0.6f + i * 0.2f, note[i], 0.55f, 2.0f, 2.5f);
                float[] chord = { 1046, 1318, 1568 };
                for (float f : chord) bell(o, 1.5f, f, 0.5f, 1.7f, 2.5f);
                return o;
            }
            default:
                return buf(0.1f);
        }
    }

    // =============================================================== the engine's loop
    /** One second of engine: low filtered noise, a hum and its octave, all on whole cycles of the
     *  loop so the end meets the start with no click. The filter runs round the loop twice and
     *  keeps the second pass, so even the noise is continuous at the seam. */
    private static float[] thrustLoop() {
        int n = RATE;
        float[] o = new float[n];
        Rng r = new Rng(77);
        float[] raw = new float[n];
        for (int i = 0; i < n; i++) raw[i] = r.next();
        float lp1 = 0f, lp2 = 0f; // two poles: a rumble, not a hiss
        float a = 1f - (float) Math.exp(-2 * Math.PI * 260 / RATE);
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < n; i++) {
                lp1 += a * (raw[i] - lp1);
                lp2 += a * (lp1 - lp2);
                if (pass == 1) o[i] = lp2 * 6.5f;
            }
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            o[i] += 0.30f * (float) Math.sin(2 * Math.PI * 62 * t)
                    + 0.14f * (float) Math.sin(2 * Math.PI * 124 * t + 0.6)
                    + 0.05f * (float) Math.sin(2 * Math.PI * 186 * t + 1.1);
            o[i] *= 0.85f + 0.15f * (float) Math.sin(2 * Math.PI * 7 * t); // a slow flutter, 7 whole cycles
        }
        return o;
    }

    // =============================================================== the parts
    private static float[] buf(float seconds) {
        return new float[(int) (seconds * RATE) + 1];
    }

    private static double wave(W w, double ph) {
        double p = ph - Math.floor(ph);
        switch (w) {
            case SINE: return Math.sin(2 * Math.PI * p);
            case TRI: return 4 * Math.abs(p - 0.5) - 1;
            case SQUARE: return p < 0.5 ? 1 : -1;
            default: return 2 * p - 1;
        }
    }

    /** A swept oscillator: its pitch glides exponentially from f0 to f1 over `dur`; its level
     *  rises over `atk` seconds then dies away at rate `decay` (per second, in e-folds). A little
     *  vibrato rides it when `vibHz` is set. */
    private static void tone(float[] o, float t0, float dur, W w, float f0, float f1, float amp,
                             float atk, float decay, float vibHz, float vibDepth) {
        int i0 = (int) (t0 * RATE), n = (int) (dur * RATE);
        double ph = 0;
        float ratio = f1 / f0;
        for (int i = 0; i < n && i0 + i < o.length; i++) {
            float t = i / (float) RATE;
            double f = f0 * Math.pow(ratio, t / dur);
            if (vibHz > 0) f *= 1 + vibDepth * Math.sin(2 * Math.PI * vibHz * t);
            ph += f / RATE;
            float env = t < atk ? t / atk : (float) Math.exp(-decay * (t - atk));
            o[i0 + i] += (float) wave(w, ph) * amp * env;
        }
    }

    /** A struck bell: the note and the overtones of metal (not whole multiples), the higher ones
     *  dying faster. `decay` is the fundamental's rate. */
    private static void bell(float[] o, float t0, float freq, float amp, float dur, float decay) {
        float[] ratio = { 1f, 2.76f, 5.40f, 8.93f };
        float[] gain = { 1f, 0.55f, 0.30f, 0.15f };
        for (int p = 0; p < ratio.length; p++) {
            float f = freq * ratio[p];
            if (f > RATE * 0.45f) continue; // nothing above the ear's ceiling here
            tone(o, t0, dur, W.SINE, f, f, amp * gain[p] * 0.55f, 0.001f, decay * (1 + 0.9f * p), 0, 0);
        }
    }

    /** A burst of noise through a filter: 'l' low-pass, 'h' high-pass, 'b' band-pass, its cutoff
     *  sweeping from c0 to c1. Shaped by the same attack and decay as a tone. */
    private static void noise(float[] o, float t0, float dur, float amp, float atk, float decay,
                              int seed, char kind, float c0, float c1) {
        int i0 = (int) (t0 * RATE), n = (int) (dur * RATE);
        Rng r = new Rng(seed * 7919 + 13);
        float ic1 = 0, ic2 = 0;
        float k = kind == 'b' ? 0.9f : 1.4f;
        float ratio = c1 / c0;
        for (int i = 0; i < n && i0 + i < o.length; i++) {
            float t = i / (float) RATE;
            float fc = Math.min(RATE * 0.45f, c0 * (float) Math.pow(ratio, t / dur));
            float g = (float) Math.tan(Math.PI * fc / RATE);
            float a1 = 1f / (1f + g * (g + k)), a2 = g * a1, a3 = g * a2;
            float v0 = r.next();
            float v3 = v0 - ic2, v1 = a1 * ic1 + a2 * v3, v2 = ic2 + a2 * ic1 + a3 * v3;
            ic1 = 2 * v1 - ic1;
            ic2 = 2 * v2 - ic2;
            float y = kind == 'l' ? v2 : kind == 'b' ? v1 * 1.6f : v0 - k * v1 - v2;
            float env = t < atk ? t / atk : (float) Math.exp(-decay * (t - atk));
            o[i0 + i] += y * amp * env;
        }
    }

    /** A small deterministic noise generator (xorshift): the same noise every run. */
    private static final class Rng {
        private int s;

        Rng(int seed) {
            s = seed == 0 ? 1 : seed;
        }

        float next() {
            s ^= s << 13;
            s ^= s >>> 17;
            s ^= s << 5;
            return ((s & 0xFFFFFF) / (float) 0x800000) - 1f;
        }
    }
}
