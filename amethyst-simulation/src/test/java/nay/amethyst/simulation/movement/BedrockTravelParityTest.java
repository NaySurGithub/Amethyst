package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regression pins for the Bedrock 1.26.52 / BDS travel helpers. */
class BedrockTravelParityTest {

    @Test
    void bubbleColumn_clampsPerDirectionAndSurface() {
        assertEquals(-0.3f, PredictionEngine.bubbleColumn(-0.29f, true, false));
        assertEquals(-0.9f, PredictionEngine.bubbleColumn(-0.89f, true, true));
        assertEquals(0.7f, PredictionEngine.bubbleColumn(0.69f, false, false));
        assertEquals(1.8f, PredictionEngine.bubbleColumn(1.75f, false, true));
        // no pre-clamp: a fast fall is only slowed by the push
        assertEquals(-1.0f + 0.06f, PredictionEngine.bubbleColumn(-1.0f, false, false), 1.0e-6f);
    }

    @Test
    void recovery_suppressesPushOutAfterTwoPenetratedMoves() {
        int flags = MovementCollisionEngine.updateRecovery(0, true, false);
        assertEquals(1.0f, MovementCollisionEngine.recoveryLimit(flags));
        flags = MovementCollisionEngine.updateRecovery(flags, true, false);
        assertEquals(0.0f, MovementCollisionEngine.recoveryLimit(flags));
        flags = MovementCollisionEngine.updateRecovery(flags, false, false);
        assertEquals(1.0f, MovementCollisionEngine.recoveryLimit(flags));
        assertEquals(0.0f, MovementCollisionEngine.recoveryLimit(
                MovementCollisionEngine.updateRecovery(0, false, true)));
    }

    @Test
    void supportingShape_picksTheClosestCentreBelow() {
        FloatBox body = new FloatBox(0.2f, 1.0f, 0.2f, 0.8f, 2.8f, 0.8f);
        FloatBox floor = new FloatBox(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f);
        FloatBox deeper = new FloatBox(0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 1.0f);
        MovementBlockPosition picked = MovementCollisionEngine.selectSupportingShape(body,
                List.of(deeper, floor));
        assertEquals(new MovementBlockPosition(0, 0, 0), picked);
        assertNull(MovementCollisionEngine.selectSupportingShape(body,
                List.of(new FloatBox(0.0f, 2.0f, 0.0f, 1.0f, 3.0f, 1.0f))));
    }

    @Test
    void searchRegion_reachesTheStepAndTheEdgeProbe() {
        FloatBox box = new FloatBox(0.2f, 1.0f, 0.2f, 0.8f, 2.8f, 0.8f);
        FloatBox region = MovementCollisionEngine.searchRegion(box, FloatVector.ZERO, 0.5625f);
        assertEquals(1.0f + 0.5625f * -1.01f, region.minY(), 1.0e-6f);
        assertEquals(2.8f + 0.5625f, region.maxY(), 1.0e-6f);
    }

    @Test
    void headInWater_usesTheEyeCellLiquidHeight() {
        TestBlockWorld world = new TestBlockWorld();
        world.put(0, 11, 0, new MovementBlockView("minecraft:water", 0.6f, true, false, false));
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(0.5f, 10.0f, 0.5f), false);
        assertTrue(MovementSimulator.headInWater(state, world));
        state.initialize(new FloatVector(0.5f, 10.38f, 0.5f), false);
        assertFalse(MovementSimulator.headInWater(state, world));
    }

    @Test
    void fluidCurrent_pushesTowardsTheLowerNeighbour() {
        TestBlockWorld world = new TestBlockWorld();
        world.put(0, 10, 0, new MovementBlockView("minecraft:flowing_water", 0.6f, true, false, false,
                1, 1, -1, 0, false, false, true));
        world.put(1, 10, 0, new MovementBlockView("minecraft:flowing_water", 0.6f, true, false, false,
                1, 3, -1, 0, false, false, true));
        float[] flow = FluidCurrent.flowAt(world, 0, 10, 0, 1);
        assertEquals(1.0f, flow[0], 1.0e-6f);
        assertEquals(0.0f, flow[2], 1.0e-6f);
    }
}
