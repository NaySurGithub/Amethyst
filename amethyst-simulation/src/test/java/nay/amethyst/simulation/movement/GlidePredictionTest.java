package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Elytra flight: gravity, slow falling, look steering and firework boosts. */
final class GlidePredictionTest {
    private static final float DELTA = 1.0E-4f;
    private static final FloatVector START = new FloatVector(0.5f, 100.0f, 0.5f);

    private static AuthoritativeMotionState glider(float pitch, boolean slowFalling) {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(START, false);
        state.ready(true);
        state.slowFalling(slowFalling);
        state.updateInput(MovementInputFrame.builder()
                .position(START)
                .delta(FloatVector.ZERO)
                .rotation(new FloatVector(pitch, 0.0f, 0.0f))
                .build());
        state.wearingElytra(true);
        state.gliding(true);
        state.onGround(false);
        state.velocity(FloatVector.ZERO);
        return state;
    }

    private static void tick(AuthoritativeMotionState state) {
        new GlidePredictionEngine(state, new TestBlockWorld(), new MovementCollisionEngine(),
                MovementConstants.CORRECTION_THRESHOLD).run();
    }

    private static boolean finite(FloatVector vector) {
        return Float.isFinite(vector.x()) && Float.isFinite(vector.y()) && Float.isFinite(vector.z());
    }

    @Test
    void levelGlide_liftsMostOfTheGravity() {
        AuthoritativeMotionState state = glider(0.0f, false);

        tick(state);

        float y = -0.08f + 0.06f;
        y += y * -0.1f;
        assertEquals(y * 0.98f, state.velocity().y(), DELTA);
    }

    @Test
    void slowFalling_usesItsOwnGravityWhileGliding() {
        AuthoritativeMotionState state = glider(0.0f, true);

        tick(state);

        float y = -0.01f + 0.0075f;
        y += y * -0.1f;
        assertEquals(y * 0.98f, state.velocity().y(), DELTA);
    }

    @Test
    void slowFalling_appliesWhileRising() {
        AuthoritativeMotionState slow = glider(0.0f, true);
        AuthoritativeMotionState normal = glider(0.0f, false);
        slow.velocity(new FloatVector(0.0f, 0.5f, 0.0f));
        normal.velocity(new FloatVector(0.0f, 0.5f, 0.0f));

        tick(slow);
        tick(normal);

        assertEquals((0.5f - 0.01f + 0.0075f) * 0.98f, slow.velocity().y(), DELTA);
        assertTrue(slow.velocity().y() > normal.velocity().y(),
                "slow falling must also lighten the climb");
    }

    @Test
    void serverClearedGravity_keepsTheGliderLevel() {
        AuthoritativeMotionState state = glider(0.0f, false);
        state.affectedByGravity(false);

        tick(state);

        assertEquals(0.0f, state.velocity().y(), DELTA);
    }

    @Test
    void lookingStraightUp_keepsTheVelocityFinite() {
        AuthoritativeMotionState state = glider(-90.0f, false);
        state.velocity(new FloatVector(0.3f, 0.0f, 0.4f));

        tick(state);

        assertTrue(finite(state.velocity()), "a vertical look must not divide by zero");
        assertTrue(finite(state.position()), "the position must stay finite");
    }

    @Test
    void lookingStraightDown_keepsTheVelocityFinite() {
        AuthoritativeMotionState state = glider(90.0f, false);
        state.velocity(new FloatVector(0.3f, -0.5f, 0.4f));

        tick(state);

        assertTrue(finite(state.velocity()), "a vertical look must not divide by zero");
    }

    @Test
    void dive_turnsFallingSpeedIntoForwardSpeed() {
        AuthoritativeMotionState state = glider(30.0f, false);
        state.velocity(new FloatVector(0.0f, -0.5f, 0.0f));

        tick(state);

        assertTrue(state.velocity().horizontalLengthSquared() > 0.0f,
                "a dive should gain horizontal speed");
        assertTrue(state.velocity().y() > -0.5f, "part of the fall should be converted");
    }

    @Test
    void climb_tradesForwardSpeedForHeight() {
        AuthoritativeMotionState state = glider(-30.0f, false);
        state.velocity(new FloatVector(0.0f, 0.0f, 1.0f));

        tick(state);

        assertTrue(state.velocity().y() > 0.0f, "looking up should climb");
        assertTrue(state.velocity().horizontalLengthSquared() < 1.0f,
                "the climb should cost horizontal speed");
    }

    @Test
    void fireworkBoost_pushesAlongTheLook() {
        AuthoritativeMotionState boosted = glider(0.0f, false);
        AuthoritativeMotionState plain = glider(0.0f, false);
        boosted.glideBoostTicks(MovementConstants.GLIDE_BOOST_TICKS);

        tick(boosted);
        tick(plain);

        assertTrue(boosted.velocity().horizontalLengthSquared()
                        > plain.velocity().horizontalLengthSquared() + 0.1f,
                "a boosted glide should be much faster");
    }

    @Test
    void glideDrag_keepsMostOfTheHorizontalSpeed() {
        AuthoritativeMotionState state = glider(0.0f, false);
        state.velocity(new FloatVector(0.0f, 0.0f, 1.0f));

        tick(state);

        float speed = (float) Math.sqrt(state.velocity().horizontalLengthSquared());
        assertTrue(speed > 0.9f && speed <= 1.0f, "glide drag retains 0.99 per tick, got " + speed);
    }
}
