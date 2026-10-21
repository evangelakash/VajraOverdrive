package vajra.voice;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;

/** The capture side: 8 kHz signed mono, read 20 ms at a time, one frame per callback —
 *  the blocking read IS the pacing. Opening fails on a machine with no microphone or no
 *  permission: that returns null and the voice channel simply never transmits. */
public final class Mic {
    public interface Frame {
        void on(short[] samples);
    }

    private final TargetDataLine line;
    private volatile boolean alive = true;

    private Mic(TargetDataLine line) {
        this.line = line;
    }

    /** Opens the default mic at the voice format and starts pumping 20 ms frames into
     *  `out`, or null when there is no mic to open. */
    public static Mic open(Frame out) {
        try {
            AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, false);
            TargetDataLine line = AudioSystem.getTargetDataLine(fmt);
            line.open(fmt, VoiceChannel.FRAME_SAMPLES * 4); // two frames of buffer
            line.start();
            Mic mic = new Mic(line);
            Thread reader = new Thread(() -> mic.loop(out), "vajra-mic");
            reader.setDaemon(true);
            reader.start();
            return mic;
        } catch (Throwable e) {
            return null; // no mic, no permission, no audio system: listen-only
        }
    }

    /** The capture loop, started by open(). Paces itself at one 20 ms frame per read. */
    private void loop(Frame out) {
        short[] samples = new short[VoiceChannel.FRAME_SAMPLES];
        byte[] buf = new byte[VoiceChannel.FRAME_SAMPLES * 2];
        while (alive) {
            int n = line.read(buf, 0, buf.length); // blocks ~20 ms — that is the pacing
            if (n < buf.length) continue;          // closed or drained mid-frame
            for (int i = 0; i < samples.length; i++) {
                samples[i] = (short) ((buf[i * 2] & 0xFF) | (buf[i * 2 + 1] << 8)); // little-endian
            }
            out.on(samples);
        }
    }

    public void dispose() {
        alive = false;
        line.stop();
        line.close();
    }
}
