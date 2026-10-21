package vajra.net;

/** Little-endian scorers for the game planes' binary bodies. The control plane travels as
 *  tagged JSON (tag 0) and voice as tagged μ-law (tag 1); the live-co-op planes (tags 2-6)
 *  are raw numbers at wire rate, so they get struct pack/unpack instead of a serializer.
 *  Every plane is [tag][senderId] then one of the bodies here; see {@link Session#route}. */
final class Bin {
    private Bin() {}

    /** LE int at an offset. */
    static void putInt(byte[] buf, int o, int v) {
        buf[o] = (byte) v;
        buf[o + 1] = (byte) (v >> 8);
        buf[o + 2] = (byte) (v >> 16);
        buf[o + 3] = (byte) (v >> 24);
    }

    static int getInt(byte[] buf, int o) {
        return (buf[o] & 0xFF) | (buf[o + 1] & 0xFF) << 8 | (buf[o + 2] & 0xFF) << 16 | (buf[o + 3] & 0xFF) << 24;
    }

    /** IEEE bits carried as an LE int — resolvable on every machine, cheap to move. */
    static void putFloat(byte[] buf, int o, float f) {
        putInt(buf, o, Float.floatToIntBits(f));
    }

    static float getFloat(byte[] buf, int o) {
        return Float.intBitsToFloat(getInt(buf, o));
    }

    static void putLong(byte[] buf, int o, long v) {
        putInt(buf, o, (int) v);
        putInt(buf, o + 4, (int) (v >>> 32));
    }

    static long getLong(byte[] buf, int o) {
        return (getInt(buf, o) & 0xFFFFFFFFL) | ((long) getInt(buf, o + 4)) << 32;
    }

    static void putU8(byte[] buf, int o, int v) {
        buf[o] = (byte) v;
    }

    static int getU8(byte[] buf, int o) {
        return buf[o] & 0xFF;
    }
}
