package vajra.voice;

import java.util.concurrent.ConcurrentHashMap;

/** Push-to-talk voice between session members: 8 kHz mono, μ-law, 20 ms frames. The
 *  session routes frames (clients → hub → everyone) and hands arrivals to
 *  {@link #receive}; this class knows only how to encode, decode, queue, and report who
 *  is audibly talking. A missing mic or speaker degrades to silence — never an error. */
public final class VoiceChannel {
    /** The session decides who gets a frame; the channel just hands them over. */
    public interface Sender {
        void route(byte[] frame);
    }

    public static final int FRAME_SAMPLES = 160; // 20 ms at 8 kHz
    static final long HEARD_FOR = 300;           // ms a talker's diamond stays lit after their last frame
    static final float TALK_LEVEL = 0.03f;       // rms above this counts as speech

    private final Sender out;
    private final Mic mic;        // null: no capture device — listen-only
    private final Speaker speaker; // null: no playback device — talk-only
    private final ConcurrentHashMap<Integer, Float> level = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> heard = new ConcurrentHashMap<>();
    private volatile boolean talking;
    private volatile int myId = -1; // set once the session's welcome lands
    private volatile boolean alive = true;

    public VoiceChannel(Sender out) {
        this.out = out;
        this.mic = Mic.open(this::onMicFrame);
        this.speaker = Speaker.open();
    }

    /** The session tells the channel who it is, so frames carry the right stamp. */
    public void setMyId(int id) {
        myId = id;
    }

    /** Push-to-talk: hold the key, say the thing. */
    public void push(boolean on) {
        talking = on;
    }

    /** Is my talk key down (for a mic-on hint on screen)? */
    public boolean transmitting() {
        return talking && mic != null && myId >= 0;
    }

    /** Do I have a microphone at all? The lobby warns the talk-only pilot. */
    public boolean hasMic() {
        return mic != null;
    }

    /** Do I have an output device at all? */
    public boolean listening() {
        return speaker != null;
    }

    /** One frame off the wire: [senderId][payload]. */
    public void receive(int senderId, byte[] payload) {
        if (!alive || senderId < 0) return;
        level.put(senderId, VoiceCodec.rms(payload));
        heard.put(senderId, System.currentTimeMillis());
        if (speaker != null) speaker.queue(payload);
    }

    /** Is this member audibly talking right now? */
    public boolean talkingOf(int memberId) {
        Long t = heard.get(memberId);
        Float l = level.get(memberId);
        return t != null && l != null
                && System.currentTimeMillis() - t < HEARD_FOR
                && l > TALK_LEVEL;
    }

    public void dispose() {
        alive = false;
        if (mic != null) mic.dispose();
        if (speaker != null) speaker.dispose();
    }

    // ---- the local mic's frame: encode + stamp + aim, only while the key is held ----------

    private void onMicFrame(short[] samples) {
        if (!talking || myId < 0 || !alive) return;
        byte[] frame = new byte[1 + VoiceChannel.FRAME_SAMPLES];
        frame[0] = (byte) myId;
        VoiceCodec.encode(samples, frame, 1);
        out.route(frame);
    }
}
