package vajra.audio;

/** 16-bit mono PCM as a WAV file's bytes: what libGDX's Sound loads. Pure, so a test can read
 *  the header back and prove the file is whole. */
public final class Wav {
    private Wav() { }

    public static byte[] encode(short[] pcm, int rate) {
        int dataLen = pcm.length * 2;
        byte[] b = new byte[44 + dataLen];
        put(b, 0, "RIFF");
        le32(b, 4, 36 + dataLen);
        put(b, 8, "WAVE");
        put(b, 12, "fmt ");
        le32(b, 16, 16);          // the fmt chunk is 16 bytes
        le16(b, 20, 1);           // PCM
        le16(b, 22, 1);           // mono
        le32(b, 24, rate);
        le32(b, 28, rate * 2);    // bytes per second
        le16(b, 32, 2);           // block align
        le16(b, 34, 16);          // bits per sample
        put(b, 36, "data");
        le32(b, 40, dataLen);
        for (int i = 0; i < pcm.length; i++) le16(b, 44 + i * 2, pcm[i]);
        return b;
    }

    private static void put(byte[] b, int at, String s) {
        for (int i = 0; i < s.length(); i++) b[at + i] = (byte) s.charAt(i);
    }

    private static void le16(byte[] b, int at, int v) {
        b[at] = (byte) v;
        b[at + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] b, int at, int v) {
        le16(b, at, v);
        le16(b, at + 2, v >> 16);
    }
}
