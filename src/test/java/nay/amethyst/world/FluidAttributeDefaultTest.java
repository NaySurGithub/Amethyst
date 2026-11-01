package nay.amethyst.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Missing fluid movement attributes fall back to the Bedrock player value. */
final class FluidAttributeDefaultTest {
    @Test
    void fallbackIsTheBedrockPlayerValue() {
        assertEquals(0.02, CompensatedHistory.PLAYER_FLUID_MOVEMENT, 0.0);
    }
}
