package nay.amethyst.simulation.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FluidGlideRiptideParityTest {
    @Test
    void levitationInFluidUsesBdsFormula() {
        assertEquals(0.1f * 0.8f + 0.02f, PredictionEngine.levitate(0.1f, 2));
    }

    @Test
    void riptideForceIsLevelPlusOneTimesThreeQuarters() {
        assertEquals(2.25f, RiptidePhysics.force(2));
    }

    @Test
    void riptideStraightUpFromGroundAddsGravityCancel() {
        FloatVector push = RiptidePhysics.impulse(0.0f, -90.0f, 0, true, false, false);
        assertEquals(0.75f + 0.08f, push.y(), 1.0e-6f);
        FloatVector air = RiptidePhysics.impulse(0.0f, -90.0f, 0);
        assertEquals(0.75f, air.y(), 1.0e-6f);
    }

    @Test
    void glideWithoutRotationChangeKeepsWrappedAngle() {
        assertEquals(10.0f, GlidePredictionEngine.interpolateAngle(10.0f, 10.0f, 1.0f));
        assertEquals(-170.0f, GlidePredictionEngine.interpolateAngle(170.0f, -170.0f, 1.0f) - 360.0f, 1.0e-4f);
    }

    @Test
    void glideLevelFlightLosesOnlyQuarterGravity() {
        FloatVector v = GlidePredictionEngine.glide(FloatVector.ZERO, 0.0f, 0.0f, 0.0f, 0.0f, false, false);
        // lift = 1: vy = 0 - (0.75 - 1) * -0.08 = -0.02, then descent and 0.98 drag
        float vy = -0.02f;
        vy = vy + (vy * -0.1f) * 1.0f;
        assertEquals(vy * 0.98f, v.y(), 1.0e-6f);
    }
}
