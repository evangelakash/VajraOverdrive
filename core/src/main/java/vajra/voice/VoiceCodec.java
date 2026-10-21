package vajra.voice;

/** G.711 μ-law — the 1970s phone codec that refuses to die: 16-bit PCM in, 8 bits out,
 *  half the wire rate of raw, plenty good enough for a held-key voice chat. Encode and
 *  decode are static and pure; the roundtrip test pins them (silence exact, tone within
 *  the codec's own quantization error). */
public final class VoiceCodec {
    private static final short[] DECODE = new short[256]; // μ-law byte -> linear sample

    static {
        for (int i = 0; i < 256; i++) {
            int u = ~i & 0xFF;
            int t = ((u & 0x0F) << 3) + 0x84;
            t <<= (u & 0x70) >> 4;
            DECODE[i] = (short) ((u & 0x80) != 0 ? 0x84 - t : t - 0x84);
        }
    }

    private VoiceCodec() {}

    /** Encode a frame of samples into the out buffer at the given offset. */
    public static void encode(short[] samples, byte[] out, int off) {
        for (int i = 0; i < samples.length; i++) out[off + i] = (byte) encode1(samples[i]);
    }

    /** Decode a μ-law frame into little-endian 16-bit PCM bytes (samples.length * 2). */
    public static void decode(byte[] frame, byte[] pcmOut) {
        for (int i = 0; i < frame.length; i++) {
            int s = DECODE[frame[i] & 0xFF];
            pcmOut[i * 2] = (byte) s;
            pcmOut[i * 2 + 1] = (byte) (s >> 8);
        }
    }

    /** The frame's loudness, normalized 0..1 — who is talking, for the roster's diamonds. */
    public static float rms(byte[] frame) {
        long sum = 0;
        for (byte b : frame) sum += (long) DECODE[b & 0xFF] * DECODE[b & 0xFF];
        return (float) Math.sqrt((double) sum / frame.length) / 32767f;
    }

    private static int encode1(int pcm) {
        int sign = (pcm >> 8) & 0x80;
        if (sign != 0) pcm = -pcm;
        if (pcm > 32635) pcm = 32635;
        pcm += 0x84;
        int exponent = 7, mask = 0x4000;
        while (exponent > 0 && (pcm & mask) == 0) {
            exponent--;
            mask >>= 1;
        }
        int mantissa = (pcm >> (exponent + 3)) & 0x0F;
        return ~(sign | (exponent << 4) | mantissa) & 0xFF;
    }
}
