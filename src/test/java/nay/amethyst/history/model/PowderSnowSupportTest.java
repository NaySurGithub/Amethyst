package nay.amethyst.history.model;

import nay.amethyst.simulation.movement.AuthoritativeMotionState;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.MovementConstants;
import nay.amethyst.simulation.movement.MovementInputFlag;
import nay.amethyst.simulation.movement.MovementInputFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** When powder snow holds a player up: leather boots, above the block, and not descending. */
final class PowderSnowSupportTest {
    private static final float TOP = 2.0f;

    private static AuthoritativeMotionState standingAt(float feetY, boolean leatherBoots,
                                                       boolean sneakHeld) {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(0.5f, feetY, 0.5f), true);
        state.wearingLeatherBoots(leatherBoots);
        MovementInputFrame.Builder input = MovementInputFrame.builder()
                .position(new FloatVector(0.5f, feetY + MovementConstants.PLAYER_HEIGHT_OFFSET, 0.5f))
                .rotation(FloatVector.ZERO);
        if (sneakHeld) {
            input.flag(MovementInputFlag.SNEAKING);
        }
        state.updateInput(input.build());
        return state;
    }

    @Test
    void leatherBoots_onTop_supports() {
        assertTrue(FrameWorldView.powderSnowSupports(standingAt(TOP, true, false), TOP));
    }

    @Test
    void noBoots_onTop_doesNotSupport() {
        assertFalse(FrameWorldView.powderSnowSupports(standingAt(TOP, false, false), TOP));
    }

    @Test
    void leatherBoots_sneakHeld_letsThePlayerSink() {
        assertFalse(FrameWorldView.powderSnowSupports(standingAt(TOP, true, true), TOP));
    }

    @Test
    void leatherBoots_alreadyInside_doesNotTrapThePlayer() {
        assertFalse(FrameWorldView.powderSnowSupports(standingAt(TOP - 0.5f, true, false), TOP));
    }
}
