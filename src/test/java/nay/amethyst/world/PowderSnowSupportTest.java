package nay.amethyst.world;

import nay.amethyst.simulation.movement.AuthoritativeMotionState;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.MovementConstants;
import nay.amethyst.simulation.movement.MovementInputFlag;
import nay.amethyst.simulation.movement.MovementInputFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Powder snow support height: leather boots, above the block, not descending; 0.9 after a long fall. */
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
            input.flag(MovementInputFlag.SNEAKING).flag(MovementInputFlag.SNEAK_DOWN);
        }
        state.updateInput(input.build());
        return state;
    }

    @Test
    void leatherBoots_onTop_supports() {
        assertTrue(1.0f == FrameWorldView.powderSnowSupportHeight(standingAt(TOP, true, false), TOP));
    }

    @Test
    void noBoots_onTop_doesNotSupport() {
        assertTrue(0.0f == FrameWorldView.powderSnowSupportHeight(standingAt(TOP, false, false), TOP));
    }

    @Test
    void leatherBoots_sneakHeld_letsThePlayerSink() {
        assertTrue(0.0f == FrameWorldView.powderSnowSupportHeight(standingAt(TOP, true, true), TOP));
    }

    @Test
    void leatherBoots_alreadyInside_doesNotTrapThePlayer() {
        assertTrue(0.0f == FrameWorldView.powderSnowSupportHeight(standingAt(TOP - 0.5f, true, false), TOP));
    }

    @Test
    void longFall_supportsAtNineTenthsWithoutBoots() {
        AuthoritativeMotionState state = standingAt(TOP, false, false);
        state.fallDistance(3.0f);
        assertTrue(0.9f == FrameWorldView.powderSnowSupportHeight(state, TOP));
    }
}
