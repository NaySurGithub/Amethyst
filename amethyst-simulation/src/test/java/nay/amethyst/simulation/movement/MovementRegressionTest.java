package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Jumping off honey and slime, the jump delay, and lava. */
final class MovementRegressionTest {
    private static final float DELTA = 1.0E-3f;

    private static AuthoritativeMotionState state(FloatVector position, boolean onGround) {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(position, onGround);
        state.ready(true);
        return state;
    }

    private static void tick(AuthoritativeMotionState state, MovementWorldView world) {
        new GroundAndAirPredictionEngine(state, world, new MovementCollisionEngine(),
                MovementConstants.CORRECTION_THRESHOLD).run();
    }

    @Test
    void honey_scalesTheJumpPower() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.HONEY);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.jumping(true);

        tick(state, world);

        assertEquals((0.42f * MovementConstants.PREVENTED_JUMP_MULTIPLIER
                        - MovementConstants.NORMAL_GRAVITY) * MovementConstants.GRAVITY_MULTIPLIER,
                state.velocity().y(), DELTA);
    }

    @Test
    void jumpDelay_blocksAnotherJump() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.jumping(true);
        state.jumpDelay(5);

        tick(state, world);

        assertTrue(state.velocity().y() <= 0.0f, "a pending jump delay must suppress the jump");
    }

    @Test
    void slime_bouncesAFallingPlayer() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.SLIME);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.5f, 0.0f));

        tick(state, world);

        assertTrue(state.velocity().y() > 0.0f, "landing on slime should bounce upward");
    }

    @Test
    void lava_dragsAndPullsDownAQuarterOfGravity() {
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.4f, 0.0f, 0.0f));
        FluidState lava = new FluidState(false, true, 1.0f, FloatVector.ZERO, 0, false);

        new LavaPredictionEngine(state, new TestBlockWorld(), new MovementCollisionEngine(),
                MovementConstants.CORRECTION_THRESHOLD, lava).run();

        assertEquals(0.4f * 0.5f, state.velocity().x(), DELTA);
        assertEquals(-MovementConstants.NORMAL_GRAVITY / 4.0f, state.velocity().y(), DELTA);
    }
}
