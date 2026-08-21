package nay.amethyst.simulation.movement;

/** Movement in water, including depth strider, current push and bubble columns. */
public final class WaterPredictionEngine extends PredictionEngine {

    static final float ACCELERATION = 0.02f;
    private static final float DRAG = 0.8f;
    private static final float FAST_DRAG = 0.9f;
    private static final float DEPTH_STRIDER_DRAG = 0.54600006f;
    private static final float DEPTH_STRIDER_STEP = 0.33333334f;
    private static final float ASCENT = 0.04f;
    private static final float PUSH = 0.014f;
    private static final float LEDGE_CLIMB = 0.3f;
    private static final float BUBBLE_DOWNWARD_MAX = -0.3f;
    private static final float BUBBLE_DOWNWARD_EXIT_MAX = -0.9f;
    private static final float BUBBLE_DOWNWARD_ACCELERATION = 0.03f;
    private static final float BUBBLE_UPWARD_MAX = 0.7f;
    private static final float BUBBLE_UPWARD_EXIT_MAX = 1.8f;
    private static final float BUBBLE_UPWARD_ACCELERATION = 0.08f;
    private static final float BUBBLE_UPWARD_EXIT_ACCELERATION = 0.1f;

    private final FluidState fluid;

    public WaterPredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
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

        float strider = 0.0f;
        if (state.depthStrider() > 0) {
            strider = DEPTH_STRIDER_STEP + DEPTH_STRIDER_STEP * (state.depthStrider() - 1);
        }
        if (!state.onGround() && state.swimming()) {
            strider *= 0.5f;
        }
        moveRelative(strider > 0.0f
                ? ACCELERATION + (state.movementSpeed() - ACCELERATION) * strider
                : ACCELERATION);

        float boxBottom = state.boundingBox().minY();
        move();

        if (!state.swimming() && !state.onGround()) {
            strider *= 0.5f;
        }
        float drag = state.sprinting() || state.stopSwimming() ? FAST_DRAG : DRAG;
        drag += (DEPTH_STRIDER_DRAG - drag) * strider;

        FloatVector velocity = state.velocity();
        state.velocity(new FloatVector(velocity.x() * drag, velocity.y() * DRAG,
                velocity.z() * drag));
        applyFluidFalling();

        if ((state.collideX() || state.collideZ()) && canClimbOut(boxBottom)) {
            FloatVector current = state.velocity();
            state.velocity(new FloatVector(current.x(), LEDGE_CLIMB, current.z()));
        }

        applyBubbleColumn();
        state.movement(state.velocity());
        return result(true, true);
    }

    private void applyFluidFalling() {
        FloatVector velocity = state.velocity();
        if (state.levitationLevel() > 0) {
            float target = (state.levitationLevel() + 1) * 0.05f;
            state.velocity(new FloatVector(velocity.x(),
                    velocity.y() + (target - velocity.y()) * 0.2f, velocity.z()));
            return;
        }
        if (state.gravity() != 0.0f && !state.swimming()) {
            state.velocity(new FloatVector(velocity.x(),
                    velocity.y() - state.gravity() / 16.0f, velocity.z()));
        }
    }

    /** Overrides the vertical velocity with the bubble column's own. */
    private void applyBubbleColumn() {
        if (fluid.bubbleDirection() == 0) {
            return;
        }

        FloatVector velocity = state.velocity();
        float y = Math.max(velocity.y(), BUBBLE_DOWNWARD_MAX);
        if (fluid.bubbleDirection() < 0) {
            y = Math.max(fluid.bubbleSurface() ? BUBBLE_DOWNWARD_EXIT_MAX : BUBBLE_DOWNWARD_MAX,
                    y - BUBBLE_DOWNWARD_ACCELERATION);
        } else {
            y = fluid.bubbleSurface()
                    ? Math.min(BUBBLE_UPWARD_EXIT_MAX, y + BUBBLE_UPWARD_EXIT_ACCELERATION)
                    : Math.min(BUBBLE_UPWARD_MAX, y + BUBBLE_UPWARD_ACCELERATION);
        }

        state.velocity(new FloatVector(velocity.x(), y, velocity.z()));
    }
}
