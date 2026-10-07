package nay.amethyst.world;

/**
 * The collision box of a bamboo stalk: a thin column from the block centre
 * towards positive X and Z, two pixels wide or three when thick, shifted
 * horizontally by an offset the game derives from the block position.
 */
final class BambooCollision {
    private static final float THIN = 2.0f / 16.0f;
    private static final float THICK = 3.0f / 16.0f;
    private static final float MIN_OFFSET = -0.25f;
    private static final float MAX_OFFSET = 0.25f;
    private static final int OFFSET_STEPS = 16;

    private BambooCollision() {
    }

    static Aabb box(int x, int y, int z, boolean thick) {
        long seed = offsetSeed(x, z);
        long[] random = {mixStafford13(seed), mixStafford13(seed + 0x9E3779B97F4A7C15L)};
        float offsetX = offsetValue(randomFloat(next(random)));
        next(random);
        float offsetZ = offsetValue(randomFloat(next(random)));
        float size = thick ? THICK : THIN;
        double minX = x + 0.5 + offsetX;
        double minZ = z + 0.5 + offsetZ;
        return new Aabb(minX, y, minZ, minX + size, y + 1.0, minZ + size);
    }

    /**
     * Both coordinate products use signed 64-bit arithmetic; keeping only a
     * signed 32-bit value after the mix is what the game does.
     */
    static long offsetSeed(int x, int z) {
        long value = (long) z * 116129781L ^ (long) x * 0x2fc20fL;
        value = (int) ((value * (value * 42317861L + 11L)) >>> 16);
        return value ^ 0x6A09E667F3BCC909L;
    }

    private static long mixStafford13(long seed) {
        seed = (seed ^ (seed >>> 30)) * 0xBF58476D1CE4E5B9L;
        seed = (seed ^ (seed >>> 27)) * 0x94D049BB133111EBL;
        return seed ^ (seed >>> 31);
    }

    /** One Xoroshiro128++ step over the two-word state. */
    private static long next(long[] state) {
        long first = state[0];
        long second = state[1];
        long result = Long.rotateLeft(first + second, 17) + first;
        second ^= first;
        state[0] = Long.rotateLeft(first, 49) ^ second ^ (second << 21);
        state[1] = Long.rotateLeft(second, 28);
        return result;
    }

    private static float randomFloat(long random) {
        return (float) (random >>> 40) * (1.0f / 16777216.0f);
    }

    private static float offsetValue(float random) {
        float index = (float) Math.floor(OFFSET_STEPS * random);
        return MIN_OFFSET + index * (MAX_OFFSET - MIN_OFFSET) / (OFFSET_STEPS - 1);
    }
}
