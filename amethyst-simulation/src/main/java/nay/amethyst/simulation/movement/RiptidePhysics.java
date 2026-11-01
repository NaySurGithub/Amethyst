package nay.amethyst.simulation.movement;

/** Riptide spin push (Bedrock 1.26.52 / BDS StartSpinAttack arithmetic, binary32). */
public final class RiptidePhysics {
    public static final int MAX_SPIN_TICKS = 20;
    public static final int GROUND_STOP_DELAY_TICKS = 5;
    public static final float ENTITY_IMPACT_MULTIPLIER = -0.2f;

    private RiptidePhysics() {
    }

    public static double strength(int level) {
        return force(level);
    }

    public static float force(int level) {
        return (((float) level + 1.0f) * 0.25f) * 3.0f;
    }

    public static FloatVector impulse(float yaw, float pitch, int level) {
        return impulse(yaw, pitch, level, false, false, false);
    }

    public static FloatVector impulse(float yaw, float pitch, int level, boolean onGround,
                                      boolean inWater, boolean headInWater) {
        float yawCoordinate = (yaw * 0.017453292f) * 10_430.378f;
        float pitchCoordinate = (0.017453292f * pitch) * 10_430.378f;
        float pitchCos = MovementConstants.sineEntry(pitchCoordinate + 16_384.0f);
        float pitchSin = MovementConstants.sineEntry(pitchCoordinate);
        float lookX = -MovementConstants.sineEntry(yawCoordinate) * pitchCos;
        float lookZ = pitchCos * MovementConstants.sineEntry(16_384.0f + yawCoordinate);
        float length = (float) Math.sqrt(lookZ * lookZ + (pitchSin * pitchSin + lookX * lookX));
        boolean tiny = length < 1.0e-4f;
        float unitX = tiny ? 0.0f : lookX / length;
        float unitY = tiny ? 0.0f : -pitchSin / length;
        float unitZ = tiny ? 0.0f : lookZ / length;
        float force = force(level);
        float pushY = unitY * force;
        if (onGround) {
            pushY = inWater && !headInWater ? (pushY / 0.8f) * 0.98f : pushY + 0.08f;
        }
        return new FloatVector(unitX * force, pushY, force * unitZ);
    }
}
