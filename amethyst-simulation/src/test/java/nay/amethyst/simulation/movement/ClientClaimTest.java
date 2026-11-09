package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the client declares about itself must not switch the simulation off. */
final class ClientClaimTest {
    private static TestBlockWorld floor() {
        TestBlockWorld world = new TestBlockWorld();
        for (int x = -5; x <= 120; x++) {
            for (int z = -3; z <= 3; z++) {
                world.solid(x, 9, z, TestBlockWorld.STONE);
            }
        }
        return world;
    }

    private static AuthoritativeMovementPipeline pipeline(float feetY, boolean onGround) {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(0.5f, feetY, 0.5f), onGround);
        state.ready(true);
        state.supportedGameMode(true);
        state.size(0.6f, 1.8f, 1.0f);
        return new AuthoritativeMovementPipeline(state, MovementOptions.defaults());
    }

    private static MovementInputFrame.Builder input(long tick, float x, float feetY, FloatVector delta) {
        return MovementInputFrame.builder().tick(tick)
                .position(new FloatVector(x, feetY + MovementConstants.PLAYER_HEIGHT_OFFSET, 0.5f))
                .delta(delta)
                .rotation(new FloatVector(0.0f, 0.0f, 0.0f))
                .moveVector(0.0f, 0.0f);
    }

    @Test
    void sunkIntoTheFloor_isStillSimulated() {
        TestBlockWorld world = floor();
        AuthoritativeMovementPipeline pipeline = pipeline(10.0f, true);
        pipeline.handle(input(1, 0.5f, 10.0f, FloatVector.ZERO).build(), world);

        for (int tick = 2; tick <= 4; tick++) {
            MovementPipelineResult result = pipeline.handle(input(tick, 0.5f + 2.0f * (tick - 1),
                    9.999f, new FloatVector(2.0f, 0.0f, 0.0f)).build(), world);
            assertTrue(result.reliable(), "a claimed overlap must not suspend the tick");
            assertTrue(result.correctionRequired());
        }
    }

    @Test
    void motionlessInsideABlock_staysSuspended() {
        TestBlockWorld world = floor().solid(0, 10, 0, TestBlockWorld.STONE);
        AuthoritativeMovementPipeline pipeline = pipeline(10.0f, true);

        MovementPipelineResult result = pipeline.handle(
                input(1, 0.5f, 10.0f, FloatVector.ZERO).build(), world);

        assertFalse(result.reliable());
        assertFalse(result.correctionRequired());
    }

    @Test
    void distantClaim_isNotFollowed() {
        TestBlockWorld world = floor();
        AuthoritativeMovementPipeline pipeline = pipeline(10.0f, true);
        pipeline.handle(input(1, 0.5f, 10.0f, FloatVector.ZERO).build(), world);

        MovementPipelineResult result = pipeline.handle(
                input(2, 50.5f, 10.0f, FloatVector.ZERO).build(), world);

        assertTrue(result.desynced());
        assertFalse(result.anchored());
        assertEquals(0.5f, result.forwardedPosition().x(), 1.0E-3f);
    }

    @Test
    void startGliding_withoutAnElytraKeepsTheSimulatedVelocity() {
        TestBlockWorld world = floor();
        AuthoritativeMovementPipeline pipeline = pipeline(10.0f, true);
        pipeline.handle(input(1, 0.5f, 10.0f, FloatVector.ZERO).build(), world);

        MovementPipelineResult result = pipeline.handle(
                input(2, 0.5f, 10.0f, new FloatVector(0.0f, 3.0f, 0.0f))
                        .flag(MovementInputFlag.START_GLIDING).build(), world);

        assertFalse(pipeline.state().gliding());
        assertTrue(result.authoritativeVelocity().y() <= 0.0f);
    }

    @Test
    void startSwimming_onDryLandDoesNotHold() {
        TestBlockWorld world = floor();
        AuthoritativeMovementPipeline pipeline = pipeline(10.0f, true);

        pipeline.handle(input(1, 0.5f, 10.0f, FloatVector.ZERO)
                .flag(MovementInputFlag.START_SWIMMING).build(), world);

        assertFalse(pipeline.state().swimming());
    }

    @Test
    void glide_neverAdoptsMoreSpeedThanItSimulated() {
        AuthoritativeMovementPipeline pipeline = pipeline(60.0f, false);
        pipeline.state().wearingElytra(true);
        TestBlockWorld world = new TestBlockWorld();
        pipeline.handle(input(1, 0.5f, 60.0f, FloatVector.ZERO)
                .flag(MovementInputFlag.START_GLIDING).build(), world);
        FloatVector feet = pipeline.state().position();

        MovementPipelineResult result = pipeline.handle(
                input(2, feet.x(), feet.y(), new FloatVector(0.0f, 0.0f, 5.0f)).build(), world);

        assertTrue(pipeline.state().gliding());
        assertTrue(result.authoritativeVelocity().length() < 1.0f,
                "declared 5 blocks a tick, simulated a fraction of one");
    }

    @Test
    void reset_keepsWhatTheClientAcknowledged() {
        AuthoritativeMotionState previous = new AuthoritativeMotionState();
        previous.abilities(true, true, false);
        previous.slowFalling(true, 200);
        previous.levitation(2, 100);
        previous.immobile(true);

        AuthoritativeMotionState next = new AuthoritativeMotionState();
        next.adoptAcknowledged(previous);

        assertTrue(next.flying());
        assertTrue(next.slowFalling());
        assertEquals(2, next.levitationLevel());
        assertTrue(next.immobile());
    }

    @Test
    void verticalCollisionInMidAir_grantsNoJump() {
        TestBlockWorld world = floor();
        AuthoritativeMovementPipeline pipeline = pipeline(20.0f, false);

        MovementPipelineResult result = pipeline.handle(
                input(1, 0.5f, 20.42f, new FloatVector(0.0f, 0.42f, 0.0f))
                        .flag(MovementInputFlag.JUMPING)
                        .flag(MovementInputFlag.VERTICAL_COLLISION).build(), world);

        assertTrue(result.authoritativeVelocity().y() <= 0.0f, "no ground under the feet, no jump");
    }

    @Test
    void verticalCollisionOnTheFloor_stillAllowsTheJump() {
        TestBlockWorld world = floor();
        AuthoritativeMovementPipeline pipeline = pipeline(10.0f, false);

        MovementPipelineResult result = pipeline.handle(
                input(1, 0.5f, 10.42f, new FloatVector(0.0f, 0.42f, 0.0f))
                        .flag(MovementInputFlag.JUMPING)
                        .flag(MovementInputFlag.VERTICAL_COLLISION).build(), world);

        assertTrue(result.authoritativeVelocity().y() > 0.0f);
    }
}
