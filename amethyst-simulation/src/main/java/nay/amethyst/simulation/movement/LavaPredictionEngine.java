package nay.amethyst.simulation.movement;

/** Movement in lava. */
public final class LavaPredictionEngine extends PredictionEngine {

    private static final float DRAG = 0.5f;
    private static final float PUSH = 0.007f;
    private static final float ASCENT = 0.04f;
    private static final float LEDGE_CLIMB = 0.3f;

    private final FluidState fluid;

    public LavaPredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                MovementCollisionEngine collisions, float correctionThreshold,
                                FluidState fluid) {
        super(state, world, collisions, correctionThreshold);
        this.fluid = fluid;
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        applyKnockback();
        applyFluidPush(fluid, PUSH);

        if (state.pressingJump() || state.autoJumpingInWater()) {
            state.velocity(state.velocity().add(0.0f, ASCENT, 0.0f));
        }

        float boxBottom = state.boundingBox().minY();
        moveRelative(WaterPredictionEngine.ACCELERATION);
        move();

        state.velocity(state.velocity().multiply(DRAG));
        if (state.gravity() != 0.0f) {
            FloatVector velocity = state.velocity();
            state.velocity(new FloatVector(velocity.x(),
                    velocity.y() - state.gravity() / 4.0f, velocity.z()));
        }

        if ((state.collideX() || state.collideZ()) && canClimbOut(boxBottom)) {
            FloatVector current = state.velocity();
            state.velocity(new FloatVector(current.x(), LEDGE_CLIMB, current.z()));
        }

        state.movement(state.velocity());
        return result(true, true);
    }
}
