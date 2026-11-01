package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression pins for Bedrock 1.26.52 / BDS movement constants. */
class BedrockMovementConstantsTest {

    @Test
    void sineTable_isTheBds126x52Table() throws Exception {
        try (InputStream in = MovementConstants.class.getResourceAsStream("bedrock_sine_1_26_52.bin")) {
            byte[] bytes = in.readAllBytes();
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals("ad80e88d62b795fa32bdd8b44a8712737c2e74ff02378134ff585a489fd380fc", digest);
        }
        assertEquals(0.0f, MovementConstants.sineEntry(0.0f));
        assertEquals(1.0f, MovementConstants.sineEntry(16_384.0f));
    }

    @Test
    void sineIndex_outOfRangeIsZero() {
        assertEquals(0, MovementConstants.sineIndex(Float.NaN));
        assertEquals(0, MovementConstants.sineIndex(3.0e9f));
        assertEquals(65_535, MovementConstants.sineIndex(-1.5f));
    }

    @Test
    void bitPatternConstants() {
        assertEquals(0x3cd4fdf3, Float.floatToRawIntBits(MovementConstants.SPRINT_AIR_SPEED));
        assertEquals(0x3c8efa35, Float.floatToRawIntBits(MovementConstants.DEGREES_TO_RADIANS));
        assertEquals(0x3da3d71a, Float.floatToRawIntBits(MovementConstants.MINIMUM_BOUNCE_SPEED));
        assertEquals(Math.ulp(1.0f), MovementConstants.FLT_EPSILON);
    }

    @Test
    void groundAcceleration_matchesTheBdsFormula() {
        float drag = 0.6f * 0.91f;
        float ratio = 0.54600006f / drag;
        assertEquals(((ratio * ratio) * ratio) * 0.1f, MovementConstants.groundAcceleration(0.1f, 0.6f));
        assertEquals(0.1f, MovementConstants.groundAcceleration(0.1f, 0.6f), 1.0e-6f);
    }

    @Test
    void damp_snapsBelowFltEpsilon() {
        assertEquals(0.0f, MovementConstants.damp(1.0e-7f, 0.546f));
        assertEquals(0.5f * 0.546f, MovementConstants.damp(0.5f, 0.546f));
    }

    @Test
    void swiftSneak_scalesTheSneakCap() {
        assertEquals(0.3f, AuthoritativeMotionState.sneakSpeed(0));
        assertEquals(0.45f, AuthoritativeMotionState.sneakSpeed(1), 1.0e-7f);
        assertEquals(0.75f, AuthoritativeMotionState.sneakSpeed(3), 1.0e-7f);
    }

    @Test
    void input_longerThanOneIsNormalised() {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.updateInput(MovementInputFrame.builder().moveVector(1.0f, 1.0f).build());
        float expected = (1.0f / (float) Math.sqrt(2.0f)) * 0.98f;
        assertEquals(expected, state.impulseForward(), 1.0e-7f);
        assertEquals(expected, state.impulseSideways(), 1.0e-7f);
    }
}
