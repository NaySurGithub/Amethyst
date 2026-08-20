package nay.amethyst.prediction.movement;

public final class BedrockMath {
    public static final float AIR_FRICTION = 0.91f;
    public static final float NORMAL_GRAVITY = 0.08f;
    public static final float NORMAL_GRAVITY_MULTIPLIER = 0.98f;
    public static final float STEP_HEIGHT = 0.5625f;
    public static final float CORRECTION_THRESHOLD = 0.3f;
    private static final float[] SIN = new float[65_536];

    static {
        for (int index = 0; index < SIN.length; index++) {
            float angle = index * (float) Math.PI * 2.0f / SIN.length;
            SIN[index] = (float) Math.sin(angle);
        }
    }

    private BedrockMath() {
    }

    public static float sin(float value) {
        return SIN[((int) (value * 10_430.378f)) & 65_535];
    }

    public static float cos(float value) {
        return SIN[((int) (value * 10_430.378f + 16_384.0f)) & 65_535];
    }

    public static float value(double value) {
        return (float) value;
    }
}
