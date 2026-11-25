package online.farmsteadminecraft.bombrun.undo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class BitPackerTest {
    @Test
    void bits() {
        assertEquals(1, BitPacker.bitsFor(1));
        assertEquals(1, BitPacker.bitsFor(2));
        assertEquals(2, BitPacker.bitsFor(3));
        assertEquals(8, BitPacker.bitsFor(256));
        assertEquals(9, BitPacker.bitsFor(257));
    }

    @Test
    void roundTripEveryWidth() {
        SplittableRandom rnd = new SplittableRandom(7);
        for (int bits = 1; bits <= 20; bits++) {
            int n = 1000 + rnd.nextInt(500);
            int[] v = new int[n];
            for (int i = 0; i < n; i++) v[i] = rnd.nextInt(1 << bits);
            long[] packed = BitPacker.pack(v, n, bits);
            assertEquals((n * (long) bits + 63) / 64, packed.length);
            assertArrayEquals(v, BitPacker.unpack(packed, n, bits));
        }
    }
}
