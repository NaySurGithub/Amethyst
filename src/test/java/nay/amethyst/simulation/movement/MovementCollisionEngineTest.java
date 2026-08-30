package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class MovementCollisionEngineTest {
    private static final float DELTA = 1.0E-3f;

    private static AuthoritativeMotionState newState(FloatVector position, boolean onGround) {
        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(position, onGround);
        return state;
    }

    @Test
    void move_solidWallOnXAxis_stopsAtWallAndSetsCollideX() {
        AuthoritativeMotionState state = newState(new FloatVector(0.0f, 10.0f, 0.0f), false);
        state.velocity(new FloatVector(1.0f, 0.0f, 0.0f));
        FloatBox wall = new FloatBox(0.5f, 9.0f, -1.0f, 1.5f, 12.0f, 1.0f);
        FixedCollisionWorld world = new FixedCollisionWorld(List.of(wall));

        new MovementCollisionEngine().move(state, world, MovementConstants.CORRECTION_THRESHOLD);

        assertEquals(0.2001f, state.position().x(), DELTA);
        assertTrue(state.collideX());
    }

    @Test
    void move_floorBelowPlayer_stopsFallAndSetsOnGround() {
        AuthoritativeMotionState state = newState(new FloatVector(0.0f, 10.0f, 0.0f), false);
        state.velocity(new FloatVector(0.0f, -1.0f, 0.0f));
        FloatBox floor = new FloatBox(-1.0f, 8.0f, -1.0f, 1.0f, 9.5f, 1.0f);
        FixedCollisionWorld world = new FixedCollisionWorld(List.of(floor));

        new MovementCollisionEngine().move(state, world, MovementConstants.CORRECTION_THRESHOLD);

        assertEquals(9.5f, state.position().y(), DELTA);
        assertTrue(state.onGround());
    }

    @Test
    void move_noCollisions_appliesFullMovementAndStaysAirborne() {
        AuthoritativeMotionState state = newState(new FloatVector(0.0f, 10.0f, 0.0f), false);
        state.velocity(new FloatVector(0.0f, -1.0f, 0.0f));
        FixedCollisionWorld world = new FixedCollisionWorld(Collections.emptyList());

        new MovementCollisionEngine().move(state, world, MovementConstants.CORRECTION_THRESHOLD);

        assertEquals(9.0f, state.position().y(), DELTA);
        assertFalse(state.onGround());
    }

    @Test
    void move_diagonalIntoCorner_resolvesYThenXThenZWithoutTunneling() {
        AuthoritativeMotionState state = newState(new FloatVector(0.0f, 10.0f, 0.0f), false);
        state.velocity(new FloatVector(1.0f, 0.0f, 1.0f));
        FloatBox corner = new FloatBox(0.5f, 9.0f, 0.5f, 1.5f, 12.0f, 1.5f);
        FixedCollisionWorld world = new FixedCollisionWorld(List.of(corner));

        new MovementCollisionEngine().move(state, world, MovementConstants.CORRECTION_THRESHOLD);

        assertFalse(state.collideX());
        assertTrue(state.collideZ());
        assertEquals(0.2001f, state.position().z(), DELTA);
        assertFalse(state.boundingBox().intersects(corner));
    }

    @Test
    void move_twoConsecutivePenetratedFrames_activatesStuckInCollider() {
        AuthoritativeMotionState state = newState(new FloatVector(0.0f, 10.0f, 0.0f), false);
        state.velocity(FloatVector.ZERO);
        FloatBox enclosing = new FloatBox(-1.0f, 9.0f, -1.0f, 1.0f, 12.0f, 1.0f);
        FixedCollisionWorld world = new FixedCollisionWorld(List.of(enclosing));

        MovementCollisionEngine engine = new MovementCollisionEngine();
        engine.move(state, world, MovementConstants.CORRECTION_THRESHOLD);
        assertFalse(state.stuckInCollider());

        state.position(new FloatVector(0.0f, 10.0f, 0.0f));
        state.velocity(FloatVector.ZERO);
        engine.move(state, world, MovementConstants.CORRECTION_THRESHOLD);
        assertTrue(state.stuckInCollider());
    }

    private static final class FixedCollisionWorld implements MovementWorldView {
        private final List<FloatBox> boxes;

        private FixedCollisionWorld(List<FloatBox> boxes) {
            this.boxes = boxes;
        }

        @Override
        public MovementBlockView block(int x, int y, int z) {
            return null;
        }

        @Override
        public List<FloatBox> collisionBoxes(FloatBox area) {
            return boxes;
        }

        @Override
        public boolean contains(FloatBox area) {
            return true;
        }

        @Override
        public boolean hasLiquidIntersection(FloatBox area) {
            return false;
        }

        @Override
        public FluidState fluidState(FloatBox area) {
            return null;
        }

        @Override
        public float underwaterSpeed() {
            return 0.0f;
        }

        @Override
        public boolean hasBambooNearby(FloatBox area) {
            return false;
        }

        @Override
        public boolean hasScaffoldingIntersection(FloatBox area) {
            return false;
        }

        @Override
        public boolean hasMovingBlock(FloatBox area) {
            return false;
        }

        @Override
        public boolean hasSolidEntityNearby(FloatBox area) {
            return false;
        }

        @Override
        public boolean hasSolidEntityIntersecting(FloatBox area) {
            return false;
        }

        @Override
        public MovementBlockPosition supportingBlock(FloatBox area, FloatVector playerPosition) {
            return null;
        }
    }
}
