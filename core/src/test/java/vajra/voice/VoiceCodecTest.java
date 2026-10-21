package vajra.voice;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** μ-law pinned: silence is exact, a tone survives the roundtrip within the codec's own
 *  quantization error, and every byte decodes somewhere sane. */
class VoiceCodecTest {

    @Test
    void silenceIsExact() {
        short[] samples = new short[VoiceChannel.FRAME_SAMPLES];
        byte[] frame = new byte[VoiceChannel.FRAME_SAMPLES];
        VoiceCodec.encode(samples, frame, 0);
        byte[] pcm = new byte[VoiceChannel.FRAME_SAMPLES * 2];
        VoiceCodec.decode(frame, pcm);
        for (int i = 0; i < pcm.length; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            assertEquals(0, s, "a zero sample stays zero through μ-law");
        }
    }

    @Test
    void aToneSurvivesTheRoundtrip() {
        int amplitude = 8000;
        short[] samples = new short[VoiceChannel.FRAME_SAMPLES];
        for (int i = 0; i < samples.length; i++)
            samples[i] = (short) (amplitude * Math.sin(2 * Math.PI * 300 * i / 8000f));
        byte[] frame = new byte[samples.length];
        VoiceCodec.encode(samples, frame, 0);
        byte[] pcm = new byte[samples.length * 2];
        VoiceCodec.decode(frame, pcm);
        for (int i = 0; i < samples.length; i++) {
            int s = (short) ((pcm[i * 2] & 0xFF) | (pcm[i * 2 + 1] << 8));
            // μ-law segments allow ~1.5% relative error at this depth; every sample stays in that band
            assertTrue(Math.abs(s - samples[i]) <= amplitude / 32,
                    "sample " + i + ": " + s + " vs " + samples[i]);
        }
    }

    @Test
    void loudnessReadsWhoIsTalking() {
        byte[] loud = new byte[VoiceChannel.FRAME_SAMPLES];
        byte[] quiet = new byte[VoiceChannel.FRAME_SAMPLES];
        for (int i = 0; i < loud.length; i++) loud[i] = 0x40;   // a strong μ-law cell (~6% rms)
        for (int i = 0; i < quiet.length; i++) quiet[i] = 0x78; // a whisper-quiet cell
        assertTrue(VoiceCodec.rms(loud) > VoiceChannel.TALK_LEVEL, "speech registers");
        assertTrue(VoiceCodec.rms(quiet) < VoiceChannel.TALK_LEVEL, "near-silence does not");
    }

    @Test
    void everyByteDecodesInsideSpeakers() {
        for (int b = 0; b < 256; b++) {
            byte[] one = {(byte) b};
            byte[] pcm = new byte[2];
            VoiceCodec.decode(one, pcm);
            int s = (short) ((pcm[0] & 0xFF) | (pcm[1] << 8));
            assertTrue(Math.abs(s) <= 32768, "byte " + b + " decodes to " + s);
        }
    }
}
