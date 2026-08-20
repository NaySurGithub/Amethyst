package nay.amethyst.simulation.movement;

public final class MovementConstants {
    public static final float AIR_FRICTION = 0.91f;
    public static final float NORMAL_GRAVITY = 0.08f;
    public static final float SLOW_FALLING_GRAVITY = 0.01f;
    public static final float GRAVITY_MULTIPLIER = 0.98f;
    public static final float LEVITATION_MULTIPLIER = 0.05f;
    public static final float STEP_HEIGHT = 0.5625f;
    public static final float SLIDE_OFFSET_MULTIPLIER = 0.4f;
    public static final float CLIMB_SPEED = 0.2f;
    public static final float CONSUMING_INPUT = 0.1225f;
    public static final float SNEAK_INPUT = 0.3f;
    public static final float PLAYER_HEIGHT_OFFSET = 1.62f;
    public static final float CORRECTION_HEIGHT_OFFSET = 1.621f;
    public static final float CORRECTION_THRESHOLD = 0.5f;
    public static final int JUMP_DELAY_TICKS = 10;
    public static final int GLIDE_BOOST_TICKS = 20;

    private static final float[] SINE = new float[65_536];
    private static final float PI = 3.1415927f;
    private static final float[] SIN_COEFFICIENTS = {
            1.5896230e-10f,
            -2.5050748e-8f,
            2.7557314e-6f,
            -1.9841270e-4f,
            8.333334e-3f,
            -1.6666667e-1f
    };
    private static final float[] COS_COEFFICIENTS = {
            -1.1358537e-11f,
            2.0875701e-9f,
            -2.7557314e-7f,
            2.4801588e-5f,
            -1.3888889e-3f,
            4.1666668e-2f
    };

    static {
        for (int index = 0; index < SINE.length; index++) {
            float angle = ((float) index * PI * 2.0f) / 65_536.0f;
            SINE[index] = floatSin(angle);
        }
    }

    private MovementConstants() {
    }

    public static float sin(float value) {
        return SINE[(int) (value * 10_430.378f) & 65_535];
    }

    public static float cos(float value) {
        return SINE[(int) (value * 10_430.378f + 16_384.0f) & 65_535];
    }

    public static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float floatSin(float value) {
        if (value == 0.0f || Float.isNaN(value)) {
            return value;
        }
        if (Float.isInfinite(value)) {
            return Float.NaN;
        }

        boolean negative = false;
        if (value < 0.0f) {
            value = -value;
            negative = true;
        }

        long octant = (long) (value * (4.0f / PI));
        float octantValue = (float) octant;
        if ((octant & 1L) == 1L) {
            octant++;
            octantValue++;
        }
        octant &= 7L;
        float reduced = ((value - octantValue * 0.7853981f)
                - octantValue * 3.7748947e-8f)
                - octantValue * 2.6951514e-15f;
        if (octant > 3L) {
            negative = !negative;
            octant -= 4L;
        }

        float squared = reduced * reduced;
        float result;
        if (octant == 1L || octant == 2L) {
            result = 1.0f - 0.5f * squared + squared * squared
                    * (((((COS_COEFFICIENTS[0] * squared + COS_COEFFICIENTS[1])
                    * squared + COS_COEFFICIENTS[2]) * squared + COS_COEFFICIENTS[3])
                    * squared + COS_COEFFICIENTS[4]) * squared + COS_COEFFICIENTS[5]);
        } else {
            result = reduced + reduced * squared
                    * (((((SIN_COEFFICIENTS[0] * squared + SIN_COEFFICIENTS[1])
                    * squared + SIN_COEFFICIENTS[2]) * squared + SIN_COEFFICIENTS[3])
                    * squared + SIN_COEFFICIENTS[4]) * squared + SIN_COEFFICIENTS[5]);
        }
        return negative ? -result : result;
    }
}
