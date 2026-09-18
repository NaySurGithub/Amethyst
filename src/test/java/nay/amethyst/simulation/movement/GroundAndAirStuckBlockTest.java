package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Blocks that hold a player back: cobweb, powder snow and sweet berry bush. */
final class GroundAndAirStuckBlockTest {
    private static final float DELTA = 1.0E-3f;
    private static final float RESTING_FALL = -MovementConstants.NORMAL_GRAVITY
            * MovementConstants.GRAVITY_MULTIPLIER;

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
    void powderSnow_insideForManyTicks_verticalVelocityNeverCompounds() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, 0.42f, 0.0f));

        for (int index = 0; index < 8; index++) {
            tick(state, world);
            assertEquals(RESTING_FALL, state.velocity().y(), DELTA,
                    "tick " + index + " kept velocity from the previous tick");
        }
    }

    @Test
    void powderSnow_inside_clearsVelocityAfterTheMove() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.5f, 0.5f, 0.5f));

        tick(state, world);

        assertEquals(0.0f, state.velocity().x(), DELTA);
        assertEquals(0.0f, state.velocity().z(), DELTA);
        assertEquals(RESTING_FALL, state.velocity().y(), DELTA);
    }

    @Test
    void powderSnow_risingVelocity_doesNotLiftThePlayer() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, 0.42f, 0.0f));
        float start = state.position().y();

        for (int index = 0; index < 8; index++) {
            tick(state, world);
        }

        assertTrue(state.position().y() <= start + 1.0f,
                "the player climbed to " + state.position().y() + " from " + start);
    }

    @Test
    void powderSnow_standingOnTop_isNotTreatedAsInside() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.velocity(new FloatVector(0.2f, 0.0f, 0.0f));

        tick(state, world);

        assertTrue(state.velocity().x() > 0.0f,
                "walking on the surface must not clear horizontal velocity");
        assertTrue(state.position().x() > 0.5f, "the player should have moved forward");
    }

    @Test
    void cobweb_inside_clearsVelocityAfterTheMove() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.COBWEB);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.5f, 0.5f, 0.5f));

        tick(state, world);

        assertEquals(0.0f, state.velocity().x(), DELTA);
        assertEquals(0.0f, state.velocity().z(), DELTA);
        assertEquals(RESTING_FALL, state.velocity().y(), DELTA);
    }

    @Test
    void cobweb_inside_barelyMovesThePlayer() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.COBWEB);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.8f, 0.0f, 0.0f));

        tick(state, world);

        assertEquals(0.5f + 0.8f * 0.25f, state.position().x(), DELTA);
    }

    @Test
    void cobweb_takesPrecedenceOverPowderSnowInTheSameSpace() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW)
                .put(0, 10, 0, TestBlockWorld.COBWEB);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.8f, 0.0f, 0.0f));

        tick(state, world);

        assertEquals(0.5f + 0.8f * 0.25f, state.position().x(), DELTA);
    }

    @Test
    void sweetBerryBush_inside_clearsVelocityAfterTheMove() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.SWEET_BERRY_BUSH);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.5f, 0.5f, 0.5f));

        tick(state, world);

        assertEquals(0.0f, state.velocity().x(), DELTA);
        assertEquals(0.0f, state.velocity().z(), DELTA);
        assertEquals(RESTING_FALL, state.velocity().y(), DELTA);
    }

    @Test
    void sweetBerryBush_inside_movesThePlayerByTheSlowedVelocity() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.SWEET_BERRY_BUSH);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.5f, 0.0f, 0.0f));

        tick(state, world);

        assertEquals(0.5f + 0.5f * 0.8f, state.position().x(), DELTA);
    }

    @Test
    void powderSnow_withLeatherBootsAndJumpHeld_climbs() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.wearingLeatherBoots(true);
        state.updateInput(input(10.0f, MovementInputFlag.JUMPING));

        tick(state, world);

        assertEquals(10.0f + 0.2f * 1.5f, state.position().y(), DELTA);
    }

    @Test
    void powderSnow_withoutLeatherBoots_jumpHeldDoesNotClimb() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.updateInput(input(10.0f, MovementInputFlag.JUMPING));

        tick(state, world);

        assertTrue(state.position().y() <= 10.0f,
                "without leather boots the player must not climb, got " + state.position().y());
    }

    @Test
    void powderSnow_sneakHeld_descends() {
        TestBlockWorld world = new TestBlockWorld().fill(8, 14, TestBlockWorld.POWDER_SNOW);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.wearingLeatherBoots(true);
        state.updateInput(input(10.0f, MovementInputFlag.SNEAKING));

        tick(state, world);

        assertEquals(10.0f - 0.15f * 1.5f, state.position().y(), DELTA);
    }

    private static MovementInputFrame input(float feetY, MovementInputFlag flag) {
        return MovementInputFrame.builder()
                .position(new FloatVector(0.5f, feetY + MovementConstants.PLAYER_HEIGHT_OFFSET, 0.5f))
                .rotation(FloatVector.ZERO)
                .flag(flag)
                .build();
    }

    @Test
    void air_doesNotClearVelocity() {
        TestBlockWorld world = new TestBlockWorld();
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.5f, 0.0f, 0.0f));

        tick(state, world);

        assertEquals(0.5f * MovementConstants.AIR_FRICTION, state.velocity().x(), DELTA);
    }
}
