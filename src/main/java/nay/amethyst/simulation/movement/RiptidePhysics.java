package nay.amethyst.simulation.movement;

import nay.amethyst.prediction.common.Vec3;

/** Exact Riptide launch impulse used by Bedrock's TridentItem::releaseUsing. */
public final class RiptidePhysics {
    public static final int MAX_SPIN_TICKS = 20;
    public static final int GROUND_STOP_DELAY_TICKS = 5;
    public static final float ENTITY_IMPACT_MULTIPLIER = -0.2f;
    private static final double RADIANS = Math.PI / 180.0;

    private RiptidePhysics() {
    }

    public static double strength(int level) {
        return (level + 1.0) * 0.75;
    }

    public static Vec3 impulse(float yaw, float pitch, int level) {
        float yawRadians = (float) (yaw * RADIANS);
        float pitchRadians = (float) (pitch * RADIANS);
        double horizontal = MovementConstants.cos(pitchRadians);
        double x = -MovementConstants.sin(yawRadians) * horizontal;
        double y = -MovementConstants.sin(pitchRadians);
        double z = MovementConstants.cos(yawRadians) * horizontal;
        double length = Math.sqrt(x * x + y * y + z * z);
        double scale = strength(level) / length;
        return new Vec3(x * scale, y * scale, z * scale);
    }
}
