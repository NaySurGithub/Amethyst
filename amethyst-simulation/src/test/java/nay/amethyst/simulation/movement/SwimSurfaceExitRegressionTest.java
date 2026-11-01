package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live false flag: sprint-swimming out of water (feet ~62.93 in water block 62), the server
 * predicted about 0.33 more horizontal travel per tick than the client while Y matched exactly.
 * The server-side default of the underwater_movement attribute (0.07) drove the water
 * acceleration instead of the Bedrock player value 0.02.
 */
class SwimSurfaceExitRegressionTest {
    private static final MovementBlockView WATER =
            new MovementBlockView("minecraft:water", 0.6f, true, false, false);

    private static float swimSpeed(float underwaterSpeed) {
        TestBlockWorld world = new TestBlockWorld().fill(50, 62, WATER).underwaterSpeed(underwaterSpeed);
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(-3.1298764f, 60.0f, 5.5276604f), false);
        state.ready(true);
        state.updateInput(MovementInputFrame.builder()
                .position(new FloatVector(-3.1298764f, 60.0f + MovementConstants.PLAYER_HEIGHT_OFFSET,
                        5.5276604f))
                .rotation(new FloatVector(0.0f, 0.0f, 90.0f))
                .moveVector(0.0f, 1.0f)
                .flag(MovementInputFlag.START_SPRINTING)
                .flag(MovementInputFlag.START_SWIMMING)
                .build());
        state.headInWater(true);
        state.swimAmount(1.0f);
        for (int i = 0; i < 60; i++) {
            FloatVector before = state.position();
            new WaterPredictionEngine(state, world, new MovementCollisionEngine(),
                    MovementConstants.CORRECTION_THRESHOLD, FluidState.NONE).run();
            // keep the swimmer at a constant depth so only the horizontal speed builds up
            state.position(new FloatVector(state.position().x(), before.y(), state.position().z()));
        }
        FloatVector v = state.velocity();
        return (float) Math.sqrt(v.x() * v.x() + v.z() * v.z());
    }

    @Test
    void sprintSwimming_terminalSpeedUsesThePlayerUnderwaterAttribute() {
        // acceleration 0.02 against the 0.9 sprint retention settles at 0.02 * 0.9 / (1 - 0.9)
        float bedrock = swimSpeed(0.02f);
        assertEquals(0.18f, bedrock, 0.005f);
        // the server default 0.07 overshoots by about the 0.33 per tick seen live
        float serverDefault = swimSpeed(0.07f);
        assertTrue(serverDefault - bedrock > 0.3f, "difference " + (serverDefault - bedrock));
    }
}
