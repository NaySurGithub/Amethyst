package nay.amethyst.simulation.movement;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MovementConstants {
    public static final float AIR_FRICTION = 0.91f;
    public static final float NORMAL_GRAVITY = 0.08f;
    public static final float SLOW_FALLING_GRAVITY = 0.01f;
    public static final float GRAVITY_MULTIPLIER = 0.98f;
    public static final float LEVITATION_MULTIPLIER = 0.05f;
    public static final float STEP_HEIGHT = 0.5625f;
    public static final float SLIDE_OFFSET_MULTIPLIER = 0.4f;
    public static final float CLIMB_SPEED = 0.2f;
    public static final float PREVENTED_JUMP_MULTIPLIER = 0.6f;
    public static final float CONSUMING_INPUT = 0.1225f;
    public static final float SNEAK_INPUT = 0.3f;
    public static final float PLAYER_HEIGHT_OFFSET = 1.62f;
    public static final float CORRECTION_HEIGHT_OFFSET = 1.621f;
    public static final float CORRECTION_THRESHOLD = 0.5f;
    public static final int JUMP_DELAY_TICKS = 10;
    public static final int GLIDE_BOOST_TICKS = 20;

    /** FLT_EPSILON, the BDS collision and damping threshold. */
    public static final float FLT_EPSILON = 1.1920929e-7f;
    /** BDS relative movement constant 0x3c8efa35. */
    public static final float DEGREES_TO_RADIANS = Float.intBitsToFloat(0x3c8efa35);
    /** Player sprint air speed, 0x3cd4fdf3, one ulp under 0.026f. */
    public static final float SPRINT_AIR_SPEED = Float.intBitsToFloat(0x3cd4fdf3);
    public static final float AIR_SPEED = 0.02f;
    /** Minimum fall speed for slime/bed restitution, 0x3da3d71a. */
    public static final float MINIMUM_BOUNCE_SPEED = Float.intBitsToFloat(0x3da3d71a);

    /**
     * Current BDS 1.26.52 sine table, as read from a running BDS 1.26.52 process
     * (bedrock_sine_1_26_52.bin, little-endian binary32).
     */
    private static final float[] SINE = loadSineTable();

    private MovementConstants() {
    }

    private static float[] loadSineTable() {
        try (InputStream in = MovementConstants.class.getResourceAsStream("bedrock_sine_1_26_52.bin")) {
            if (in == null) throw new IllegalStateException("missing bedrock_sine_1_26_52.bin");
            byte[] bytes = in.readAllBytes();
            if (bytes.length != 65_536 * 4) throw new IllegalStateException("bad sine table size");
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            float[] table = new float[65_536];
            for (int i = 0; i < table.length; i++) table[i] = buffer.getFloat();
            return table;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Sine table index: out of int32 range gives 0. */
    public static int sineIndex(float coordinate) {
        if (!(coordinate >= -2147483648.0f && coordinate < 2147483648.0f)) return 0;
        return ((int) coordinate) & 0xFFFF;
    }

    /** Table entry at an already scaled coordinate. */
    public static float sineEntry(float coordinate) {
        return SINE[sineIndex(coordinate)];
    }

    /** Table sine of a radian angle: index = (int)(rad * 10430.378f). */
    public static float sin(float value) {
        return SINE[sineIndex(value * 10_430.378f)];
    }

    /** Table cosine: index = (int)(Round(rad * 10430.378f) + 16384f). */
    public static float cos(float value) {
        return SINE[sineIndex(value * 10_430.378f + 16_384.0f)];
    }

    /**
     * CRT binary32 sinf as used by current BDS relative movement.
     * Evaluated in double and rounded once to float, as glibc/Apple/UCRT sinf do.
     */
    public static float crtSin(float value) {
        return (float) StrictMath.sin(value);
    }

    public static float crtCos(float value) {
        return (float) StrictMath.cos(value);
    }

    /** BDS friction damping. */
    public static float damp(float velocity, float retention) {
        return velocity * (Math.abs(velocity) > FLT_EPSILON ? retention : 0.0f);
    }

    /** BDS ground acceleration. */
    public static float groundAcceleration(float speed, float friction) {
        float drag = friction * 0.91f;
        float ratio = 0.54600006f / (drag == 0.0f ? 0.54600006f : drag);
        return ((ratio * ratio) * ratio) * speed;
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
