package nay.amethyst.simulation.movement;

/** Elytra flight, driven by the look vector. */
public final class GlidePredictionEngine extends PredictionEngine {

    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0f;

    public GlidePredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                 MovementCollisionEngine collisions, float correctionThreshold) {
        super(state, world, collisions, correctionThreshold);
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        applyKnockback();
        float yaw = state.rotation().z() * DEGREES_TO_RADIANS;
        float pitch = state.rotation().x() * DEGREES_TO_RADIANS;
        float yawCosine = MovementConstants.cos(-yaw - (float) Math.PI);
        float yawSine = MovementConstants.sin(-yaw - (float) Math.PI);
        float pitchCosine = MovementConstants.cos(pitch);
        float pitchSine = MovementConstants.sin(pitch);

        float lookX = yawSine * -pitchCosine;
        float lookY = -pitchSine;
        float lookZ = yawCosine * -pitchCosine;
        FloatVector velocity = state.velocity();
        float x = velocity.x();
        float y = velocity.y();
        float z = velocity.z();
        float horizontalVelocity = (float) Math.sqrt(x * x + z * z);
        float horizontalLook = pitchCosine;
        float squaredPitchCosine = pitchCosine * pitchCosine;

        y += -0.08f + squaredPitchCosine * 0.06f;
        if (y < 0.0f && horizontalLook > 0.0f) {
            float acceleration = y * -0.1f * squaredPitchCosine;
            y += acceleration;
            x += lookX * acceleration / horizontalLook;
            z += lookZ * acceleration / horizontalLook;
        }
        if (pitch < 0.0f) {
            float acceleration = horizontalVelocity * -pitchSine * 0.04f;
            y += acceleration * 3.2f;
            x -= lookX * acceleration / horizontalLook;
            z -= lookZ * acceleration / horizontalLook;
        }
        if (horizontalLook > 0.0f) {
            x += (lookX / horizontalLook * horizontalVelocity - x) * 0.1f;
            z += (lookZ / horizontalLook * horizontalVelocity - z) * 0.1f;
        }
        if (state.glideBoostTicks() > 0) {
            x += lookX * 0.1f + (lookX * 1.5f - x) * 0.5f;
            y += lookY * 0.1f + (lookY * 1.5f - y) * 0.5f;
            z += lookZ * 0.1f + (lookZ * 1.5f - z) * 0.5f;
        }
        state.velocity(new FloatVector(x * 0.99f, y * 0.98f, z * 0.99f));
        move();
        state.movement(state.velocity());
        return result(true);
    }
}
