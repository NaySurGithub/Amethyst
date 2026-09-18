package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gravity, friction, jumping and the blocks that change how the ground behaves. */
final class GroundAndAirPhysicsTest {
    private static final float DELTA = 1.0E-3f;

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
    void airborne_appliesGravityAndAirFriction() {
        TestBlockWorld world = new TestBlockWorld();
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 20.0f, 0.5f), false);
        state.velocity(new FloatVector(0.4f, 0.0f, 0.0f));

        tick(state, world);

        assertEquals(-MovementConstants.NORMAL_GRAVITY * MovementConstants.GRAVITY_MULTIPLIER,
                state.velocity().y(), DELTA);
        assertEquals(0.4f * MovementConstants.AIR_FRICTION, state.velocity().x(), DELTA);
    }

    @Test
    void airborne_fallsFasterEachTick() {
        TestBlockWorld world = new TestBlockWorld();
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 40.0f, 0.5f), false);

        tick(state, world);
        float first = state.velocity().y();
        tick(state, world);
        float second = state.velocity().y();

        assertTrue(second < first, "the fall should accelerate");
        assertTrue(second > -3.92f, "terminal velocity must not be exceeded in two ticks");
    }

    @Test
    void ground_appliesBlockFrictionOnTopOfAirFriction() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.velocity(new FloatVector(0.4f, 0.0f, 0.0f));

        tick(state, world);

        assertEquals(0.4f * MovementConstants.AIR_FRICTION * 0.6f, state.velocity().x(), DELTA);
    }

    @Test
    void ice_keepsFarMoreSpeedThanStone() {
        TestBlockWorld stoneWorld = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        TestBlockWorld iceWorld = new TestBlockWorld().floor(9, TestBlockWorld.ICE);
        AuthoritativeMotionState onStone = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        AuthoritativeMotionState onIce = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        onStone.velocity(new FloatVector(0.4f, 0.0f, 0.0f));
        onIce.velocity(new FloatVector(0.4f, 0.0f, 0.0f));

        tick(onStone, stoneWorld);
        tick(onIce, iceWorld);

        assertTrue(onIce.velocity().x() > onStone.velocity().x() * 1.5f,
                "ice should preserve much more speed than stone");
    }

    @Test
    void jump_liftsThePlayerAndArmsTheJumpDelay() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.jumping(true);

        tick(state, world);

        assertEquals((0.42f - MovementConstants.NORMAL_GRAVITY)
                * MovementConstants.GRAVITY_MULTIPLIER, state.velocity().y(), DELTA);
        assertEquals(MovementConstants.JUMP_DELAY_TICKS, state.jumpDelay());
        assertFalse(state.onGround(), "the player should have left the ground");
    }

    @Test
    void jump_whileAirborne_isIgnored() {
        TestBlockWorld world = new TestBlockWorld();
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 20.0f, 0.5f), false);
        state.jumping(true);

        tick(state, world);

        assertEquals(-MovementConstants.NORMAL_GRAVITY * MovementConstants.GRAVITY_MULTIPLIER,
                state.velocity().y(), DELTA);
        assertEquals(0, state.jumpDelay());
    }

    @Test
    void sprintJump_addsForwardImpulse() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState sprinting = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        AuthoritativeMotionState walking = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        sprinting.sprinting(true);
        sprinting.jumping(true);
        walking.jumping(true);

        tick(sprinting, world);
        tick(walking, world);

        assertTrue(sprinting.velocity().horizontalLengthSquared()
                        > walking.velocity().horizontalLengthSquared(),
                "a sprint jump should carry more horizontal speed");
    }

    @Test
    void honeyBlock_underPlayer_preventsFullJumpHeight() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.HONEY);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.jumping(true);

        tick(state, world);

        assertEquals((0.42f * MovementConstants.PREVENTED_JUMP_MULTIPLIER
                        - MovementConstants.NORMAL_GRAVITY) * MovementConstants.GRAVITY_MULTIPLIER,
                state.velocity().y(), DELTA);
    }

    @Test
    void honeyBlock_walkingOn_slowsHorizontalSpeed() {
        TestBlockWorld honeyWorld = new TestBlockWorld().floor(9, TestBlockWorld.HONEY);
        TestBlockWorld stoneWorld = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState onHoney = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        AuthoritativeMotionState onStone = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        onHoney.velocity(new FloatVector(0.3f, 0.0f, 0.0f));
        onStone.velocity(new FloatVector(0.3f, 0.0f, 0.0f));

        tick(onHoney, honeyWorld);
        tick(onStone, stoneWorld);

        assertTrue(onHoney.velocity().x() < onStone.velocity().x(),
                "honey should slow the player down");
    }

    @Test
    void slime_landingOnIt_bouncesBackUp() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.SLIME);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.4f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.6f, 0.0f));

        tick(state, world);

        assertTrue(state.velocity().y() > 0.0f,
                "landing on slime should send the player back up, got " + state.velocity().y());
    }

    @Test
    void stone_landingOnIt_doesNotBounce() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.4f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.6f, 0.0f));

        tick(state, world);

        assertTrue(state.velocity().y() <= 0.0f, "stone must not bounce");
        assertTrue(state.onGround(), "the player should have landed");
    }

    @Test
    void ladder_clampsTheFallSpeed() {
        TestBlockWorld world = new TestBlockWorld().put(0, 10, 0, TestBlockWorld.LADDER);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.8f, 0.0f));

        tick(state, world);

        assertTrue(state.velocity().y() > -0.3f,
                "a ladder should slow the descent, got " + state.velocity().y());
    }

    @Test
    void levitation_pushesThePlayerUpward() {
        TestBlockWorld world = new TestBlockWorld();
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 20.0f, 0.5f), false);
        state.levitationLevel(1);

        tick(state, world);

        assertTrue(state.velocity().y() > 0.0f,
                "levitation should lift the player, got " + state.velocity().y());
    }

    @Test
    void soulSand_reducesWalkingAcceleration() {
        TestBlockWorld soulSandWorld = new TestBlockWorld().floor(9, TestBlockWorld.SOUL_SAND);
        TestBlockWorld stoneWorld = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState onSoulSand = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        AuthoritativeMotionState onStone = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        MovementInputFrame input = MovementInputFrame.builder()
                .position(new FloatVector(0.5f, 10.0f + MovementConstants.PLAYER_HEIGHT_OFFSET, 0.5f))
                .rotation(FloatVector.ZERO)
                .moveVector(0.0f, 1.0f)
                .build();
        onSoulSand.updateInput(input);
        onStone.updateInput(input);

        tick(onSoulSand, soulSandWorld);
        tick(onStone, stoneWorld);

        assertTrue(onSoulSand.velocity().horizontalLengthSquared()
                        < onStone.velocity().horizontalLengthSquared(),
                "soul sand should accelerate the player less than stone");
    }

    @Test
    void walkingIntoAWall_stopsHorizontalMovement() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE)
                .solid(1, 10, 0, TestBlockWorld.STONE)
                .solid(1, 11, 0, TestBlockWorld.STONE);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);
        state.velocity(new FloatVector(0.5f, 0.0f, 0.0f));

        tick(state, world);

        assertTrue(state.collideX(), "the wall should have been hit");
        assertEquals(0.0f, state.velocity().x(), DELTA);
    }

    @Test
    void standingStill_staysOnTheGround() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.STONE);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.0f, 0.5f), true);

        for (int index = 0; index < 5; index++) {
            tick(state, world);
        }

        assertTrue(state.onGround(), "the player should still be supported");
        assertEquals(10.0f, state.position().y(), DELTA);
    }

    @Test
    void bed_landingOnIt_bouncesAtThreeQuarters() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.BED);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 10.4f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.6f, 0.0f));

        tick(state, world);

        assertEquals((0.75f * 0.6f - MovementConstants.NORMAL_GRAVITY)
                * MovementConstants.GRAVITY_MULTIPLIER, state.velocity().y(), DELTA);
    }

    @Test
    void bed_hardLanding_isNotCapped() {
        TestBlockWorld world = new TestBlockWorld().floor(9, TestBlockWorld.BED);
        AuthoritativeMotionState state = state(new FloatVector(0.5f, 11.9f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -2.0f, 0.0f));

        tick(state, world);

        assertEquals((0.75f * 2.0f - MovementConstants.NORMAL_GRAVITY)
                * MovementConstants.GRAVITY_MULTIPLIER, state.velocity().y(), DELTA);
    }

    @Test
    void honeyWall_touchingItWhileFalling_slidesSlowly() {
        TestBlockWorld world = new TestBlockWorld();
        for (int y = 8; y <= 14; y++) {
            world.put(1, y, 0, TestBlockWorld.HONEY);
        }
        AuthoritativeMotionState state = state(new FloatVector(0.7f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.5f, 0.0f));

        tick(state, world);

        assertEquals(-0.12f, state.velocity().y(), DELTA);
    }

    @Test
    void honeyWall_notTouchingIt_fallsNormally() {
        TestBlockWorld world = new TestBlockWorld();
        for (int y = 8; y <= 14; y++) {
            world.put(3, y, 0, TestBlockWorld.HONEY);
        }
        AuthoritativeMotionState state = state(new FloatVector(0.7f, 10.0f, 0.5f), false);
        state.velocity(new FloatVector(0.0f, -0.5f, 0.0f));

        tick(state, world);

        assertEquals((-0.5f - MovementConstants.NORMAL_GRAVITY) * MovementConstants.GRAVITY_MULTIPLIER,
                state.velocity().y(), DELTA);
    }
}
