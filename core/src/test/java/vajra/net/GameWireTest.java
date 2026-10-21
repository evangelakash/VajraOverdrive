package vajra.net;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import vajra.enemy.Enemy;

/** The wire's own small laws: little-endian round trips, the five plane constants, and the
 *  flavour bits that ride the swarm's foe rows — a kind ordinal fits its mask. */
class GameWireTest {

    @Test
    void binRoundTripsLittleEndian() {
        byte[] b = new byte[16];
        Bin.putInt(b, 0, 0x12345678);
        assertEquals(0x12345678, Bin.getInt(b, 0));
        Bin.putFloat(b, 4, 3.75f);
        assertEquals(3.75f, Bin.getFloat(b, 4));
        Bin.putLong(b, 8, 0x0102030405060708L);
        assertEquals(0x0102030405060708L, Bin.getLong(b, 8));
    }

    @Test
    void u8ReadsAreUnsigned() {
        byte[] b = new byte[2];
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0x80;
        assertEquals(255, Bin.getU8(b, 0));
        assertEquals(128, Bin.getU8(b, 1));
    }

    @Test
    void planeTagsStayStable() {
        assertEquals(2, GameWire.SHIP);
        assertEquals(3, GameWire.SHOT);
        assertEquals(4, GameWire.SWARM);
        assertEquals(5, GameWire.PAIN);
        assertEquals(6, GameWire.TAKE);
    }

    @Test
    void aKindFitsTheSwarmFlavourBits() {
        // the swarm's flavour byte packs 0x1F ordinal + boss/elite/rank1 masks (0x40/0x80/0x20)
        assertTrue(Enemy.Kind.values().length <= 0x1F + 1, "kinds outgrew the flavour byte");
    }
}
