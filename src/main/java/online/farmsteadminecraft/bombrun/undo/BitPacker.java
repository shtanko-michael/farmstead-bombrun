package online.farmsteadminecraft.bombrun.undo;

/**
 * Packs small non-negative ints (palette indices) into longs with the fewest bits that hold the largest one,
 * the way a chunk section stores its block states (PLAN.md §5.4). Values may span two longs. Plain Java.
 */
public final class BitPacker {
    private BitPacker() {
    }

    /** Bits needed for values {@code 0 .. paletteSize-1}; at least 1. */
    public static int bitsFor(int paletteSize) {
        return Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, paletteSize - 1)));
    }

    public static long[] pack(int[] values, int count, int bits) {
        if (bits < 1 || bits > 32) throw new IllegalArgumentException("bits " + bits);
        long[] out = new long[(int) (((long) count * bits + 63) / 64)];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            long v = values[i] & mask;
            if (values[i] < 0 || values[i] > mask) throw new IllegalArgumentException("value " + values[i] + " does not fit " + bits + " bits");
            long bit = (long) i * bits;
            int word = (int) (bit >>> 6);
            int off = (int) (bit & 63);
            out[word] |= v << off;
            if (off + bits > 64) out[word + 1] |= v >>> (64 - off);
        }
        return out;
    }

    public static int[] unpack(long[] data, int count, int bits) {
        if (bits < 1 || bits > 32) throw new IllegalArgumentException("bits " + bits);
        if ((long) count * bits > (long) data.length * 64) throw new IllegalArgumentException("data too short");
        int[] out = new int[count];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < count; i++) {
            long bit = (long) i * bits;
            int word = (int) (bit >>> 6);
            int off = (int) (bit & 63);
            long v = data[word] >>> off;
            if (off + bits > 64) v |= data[word + 1] << (64 - off);
            out[i] = (int) (v & mask);
        }
        return out;
    }
}
