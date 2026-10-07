package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Standing on, sneaking down through and jumping up inside scaffolding. */
final class ScaffoldingTest {
    private static final float DELTA = 1.0E-3f;

    private static AuthoritativeMotionState player(FloatVector position, boolean onGround,
                                                   MovementInputFlag... flags) {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(position, onGround);
        state.ready(true);
        MovementInputFrame.Builder input = MovementInputFrame.builder()
                .position(position.add(0.0f, MovementConstants.PLAYER_HEIGHT_OFFSET, 0.0f))
                .delta(FloatVector.ZERO)
                .rotation(FloatVector.ZERO);
        for (MovementInputFlag flag : flags) {
            input.flag(flag);
        }
        state.updateInput(input.build());
        state.onGround(onGround);
        state.velocity(FloatVector.ZERO);
        return state;
    }

    private static void tick(AuthoritativeMotionState state, MovementWorldView world) {
        state.descendingScaffold(false);
        new GroundAndAirPredictionEngine(state, world, new MovementCollisionEngine(),
                MovementConstants.CORRECTION_THRESHOLD).run();
    }

    /** A column of scaffolding from y=5 to y=9 standing on stone, with its top at y=10. */
    private static TestBlockWorld column() {
        TestBlockWorld world = new TestBlockWorld().solid(0, 4, 0, TestBlockWorld.STONE);
        for (int y = 5; y <= 9; y++) {
            world.scaffolding(0, y, 0);
        }
        return world;
    }

    @Test
    void standingOnTop_holdsThePlayer() {
        TestBlockWorld world = column();
        AuthoritativeMotionState state = player(new FloatVector(0.5f, 10.0f, 0.5f), true);

        tick(state, world);

        assertEquals(10.0f, state.position().y(), DELTA);
        assertTrue(state.onGround(), "the scaffolding top should hold a standing player");
    }

    @Test
    void sneakingOverASupportedColumn_descendsAtAFixedSpeed() {
        TestBlockWorld world = column();
        AuthoritativeMotionState state = player(new FloatVector(0.5f, 10.0f, 0.5f), true,
                MovementInputFlag.SNEAK_DOWN, MovementInputFlag.SNEAKING);

        tick(state, world);

        assertEquals(10.0f - 0.15f, state.position().y(), DELTA);
        assertEquals(-0.15f * MovementConstants.GRAVITY_MULTIPLIER, state.velocity().y(), DELTA);
    }

    @Test
    void sneakingOnABridgeOverAir_staysOnTop() {
        TestBlockWorld world = new TestBlockWorld().scaffolding(0, 9, 0);
        AuthoritativeMotionState state = player(new FloatVector(0.5f, 10.0f, 0.5f), true,
                MovementInputFlag.SNEAK_DOWN, MovementInputFlag.SNEAKING);

        tick(state, world);

        assertEquals(10.0f, state.position().y(), DELTA);
        assertFalse(state.descendingScaffold(), "an unsupported scaffold must not let the player down");
    }

    @Test
    void jumpingInside_climbsWithoutAGroundJump() {
        TestBlockWorld world = column();
        AuthoritativeMotionState state = player(new FloatVector(0.5f, 7.0f, 0.5f), false,
                MovementInputFlag.JUMPING);

        tick(state, world);

        assertEquals((0.15f - MovementConstants.NORMAL_GRAVITY) * MovementConstants.GRAVITY_MULTIPLIER,
                state.velocity().y(), DELTA);
        assertEquals(MovementConstants.JUMP_DELAY_TICKS, state.jumpDelay());
    }

    @Test
    void walkingIntoTheSide_passesThrough() {
        TestBlockWorld world = new TestBlockWorld().floor(4, TestBlockWorld.STONE).scaffolding(1, 5, 0);
        AuthoritativeMotionState state = player(new FloatVector(0.5f, 5.0f, 0.5f), true);
        state.velocity(new FloatVector(0.5f, 0.0f, 0.0f));

        tick(state, world);

        assertFalse(state.collideX(), "scaffolding must not block the player from the side");
    }
}
