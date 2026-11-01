package nay.amethyst.simulation.movement;

/** Elytra flight, driven by the look vector (Bedrock 1.26.52 / BDS glide move arithmetic). */
public final class GlidePredictionEngine extends PredictionEngine {

    private static final float NEGATIVE_PI = Float.intBitsToFloat(0xc0490fdb);

    public GlidePredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                 MovementCollisionEngine collisions, float correctionThreshold) {
        super(state, world, collisions, correctionThreshold);
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        return travel(Medium.GLIDE);
    }

    static float interpolateAngle(float previous, float current, float alpha) {
        float delta = ((current - previous) + 180.0f) % 360.0f;
        if (delta < 0) delta += 360.0f;
        float movement = (delta - 180.0f) * alpha;
        return movement + previous;
    }

    private static float sine(float coordinate) {
        return MovementConstants.sineEntry(coordinate);
    }

    static FloatVector glide(FloatVector velocity, float previousPitch, float previousYaw,
                             float pitchNow, float yawNow, boolean slowFalling, boolean boost) {
        float vx = velocity.x();
        float vy = velocity.y();
        float vz = velocity.z();
        float yaw = interpolateAngle(previousYaw, yawNow, 1.0f);
        float pitch = interpolateAngle(previousPitch, pitchNow, 1.0f);
        float yawCoordinate = ((yaw * -0.017453292f) + NEGATIVE_PI) * 10_430.378f;
        float pitchCoordinate = (pitch * -0.017453292f) * 10_430.378f;
        float horizontal = -sine(pitchCoordinate + 16_384.0f);
        float lookX = horizontal * sine(yawCoordinate);
        float lookY = sine(pitchCoordinate);
        float lookZ = horizontal * sine(yawCoordinate + 16_384.0f);
        float radians = pitchNow * 0.017453292f;
        float horizontalLookSquared = lookX * lookX + lookZ * lookZ;
        float horizontalLook = (float) Math.sqrt(horizontalLookSquared);
        float horizontalSpeed = (float) Math.sqrt(vx * vx + vz * vz);
        float trigCoordinate = radians * 10_430.378f;
        float cosine = sine(trigCoordinate + 16_384.0f);
        float lookLength = (float) Math.sqrt((lookY * lookY + lookX * lookX) + lookZ * lookZ);
        float lift = (Math.min(lookLength / 0.4f, 1.0f) * cosine) * cosine;
        float gravity = slowFalling ? -0.01f : -0.08f;
        vy = vy - ((lift * 0.75f) + -1.0f) * gravity;
        if (horizontalLookSquared > 0 && vy < 0) {
            float descent = (vy * -0.1f) * lift;
            vy = vy + descent;
            vx = vx + (descent * lookX) / horizontalLook;
            vz = vz + (descent * lookZ) / horizontalLook;
        }
        if (radians < 0) {
            float climb = (sine(trigCoordinate) * horizontalSpeed) * -0.04f;
            vy = vy + climb * 3.2f;
            vx = vx - (climb * lookX) / horizontalLook;
            vz = vz - (climb * lookZ) / horizontalLook;
        }
        if (horizontalLookSquared > 0) {
            vx = vx + (horizontalSpeed * (lookX / horizontalLook) - vx) * 0.1f;
            vz = vz + (horizontalSpeed * (lookZ / horizontalLook) - vz) * 0.1f;
        }
        if (boost) {
            vx = boosted(vx, lookX);
            vy = boosted(vy, lookY);
            vz = boosted(vz, lookZ);
        }
        return new FloatVector(vx * 0.99f, vy * 0.98f, vz * 0.99f);
    }

    private static float boosted(float speed, float direction) {
        return speed + ((direction * 1.5f - speed) * 0.5f + direction * 0.1f);
    }
}
