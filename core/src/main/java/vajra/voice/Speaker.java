package vajra.voice;

import java.util.concurrent.ConcurrentLinkedQueue;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** The playback side: frames queue into a jitter buffer and a writer thread drains them
 *  into the line, paced by the line's own 20 ms appetite. When the buffer runs dry it
 *  writes silence so the line never starves and restarts; when it runs ten deep, the
 *  oldest frame is dropped — old words don't belong in a fight. */
public final class Speaker {
    private final SourceDataLine line;
    private final ConcurrentLinkedQueue<byte[]> jitter = new ConcurrentLinkedQueue<>();
    private volatile boolean alive = true;

    private Speaker(SourceDataLine line) {
        this.line = line;
    }

    /** Opens the default output at the voice format, or null when there isn't one. */
    public static Speaker open() {
        try {
            AudioFormat fmt = new AudioFormat(8000f, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, VoiceChannel.FRAME_SAMPLES * 8); // ~80 ms of buffer
            line.start();
            Speaker speaker = new Speaker(line);
            Thread writer = new Thread(speaker::loop, "vajra-speaker");
            writer.setDaemon(true);
            writer.start();
            return speaker;
        } catch (Throwable e) {
            return null; // no playback device: talk-only
        }
    }

    /** One μ-law frame off the wire. */
    public void queue(byte[] frame) {
        if (jitter.size() >= 10) jitter.poll(); // too far behind: drop the oldest
        jitter.add(frame);
    }

    /** The playback loop, started by open(). line.write blocks until the hardware
     *  consumes the frame — that is the pacing, no sleeps anywhere. */
    private void loop() {
        byte[] silence = new byte[VoiceChannel.FRAME_SAMPLES * 2];
        byte[] pcm = new byte[VoiceChannel.FRAME_SAMPLES * 2];
        while (alive) {
            byte[] frame = jitter.poll();
            if (frame == null) {
                line.write(silence, 0, silence.length); // keeps the line warm and the loop paced
                continue;
            }
            VoiceCodec.decode(frame, pcm);
            line.write(pcm, 0, pcm.length);
        }
    }

    public void dispose() {
        alive = false;
        line.stop(); // unblocks a pending write; the loop exits inside ~80 ms
        line.close();
    }
}
