package nay.amethyst.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Setback targets follow every accepted tick, not only ground ticks. */
final class SetbackTargetTest {
    @Test
    void acceptedTick_inAirOrWater_isATarget() {
        assertTrue(SetbackTargets.accepted(0.0, 0.0, true));
    }

    @Test
    void offsetTick_orBufferedExcess_isNotATarget() {
        assertFalse(SetbackTargets.accepted(0.0, 0.01, true));
        assertFalse(SetbackTargets.accepted(0.2, 0.0, true));
        assertFalse(SetbackTargets.accepted(0.0, 0.0, false));
    }
}
