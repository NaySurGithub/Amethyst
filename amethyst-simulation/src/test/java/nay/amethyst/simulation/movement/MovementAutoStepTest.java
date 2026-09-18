package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class MovementAutoStepTest {

    private static final float EPSILON = 1.0E-3f;
    private static final float HALF_WIDTH = 0.3f;
    private static final float EDGE_INSET = 0.025f;

    @Test
    void move_stepHeightBlockAhead_climbsWithoutJumping() {
        FloatBox floor = new FloatBox(-5.0f, -1.0f, -5.0f, 5.0f, 0.0f, 5.0f);
        FloatBox step = new FloatBox(0.5f, 0.0f, -5.0f, 5.0f, MovementConstants.STEP_HEIGHT, 5.0f);
        FakeWorldView world = new FakeWorldView(List.of(floor, step));

        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(0.0f, 0.0f, 0.0f), true);
        state.velocity(new FloatVector(0.4f, 0.0f, 0.0f));

        MovementCollisionEngine engine = new MovementCollisionEngine();
        engine.move(state, world, 0.0f);

        assertTrue(state.position().y() > MovementConstants.STEP_HEIGHT - EPSILON,
                "player should have climbed onto the step");
    }

    @Test
    void move_fullBlockAhead_isBlockedNotClimbed() {
        FloatBox floor = new FloatBox(-5.0f, -1.0f, -5.0f, 5.0f, 0.0f, 5.0f);
        FloatBox wall = new FloatBox(0.5f, 0.0f, -5.0f, 5.0f, 1.0f, 5.0f);
        FakeWorldView world = new FakeWorldView(List.of(floor, wall));

        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(0.0f, 0.0f, 0.0f), true);
        state.velocity(new FloatVector(0.4f, 0.0f, 0.0f));

        MovementCollisionEngine engine = new MovementCollisionEngine();
        engine.move(state, world, 0.0f);

        assertTrue(state.position().y() < EPSILON,
                "player should not have gained height against a full block");
        assertTrue(state.position().x() < 0.4f - EPSILON,
                "horizontal movement should have been blocked by the wall");
    }

    @Test
    void move_sneakingAtEdge_reducesHorizontalMovementToAvoidFalling() {
        FloatBox floor = new FloatBox(-5.0f, -1.0f, -5.0f, 0.0f, 0.0f, 5.0f);
        FakeWorldView world = new FakeWorldView(List.of(floor));

        AuthoritativeMotionState state = new AuthoritativeMotionState();
        state.initialize(new FloatVector(0.0f, 0.0f, 0.0f), true);
        state.updateInput(MovementInputFrame.builder()
                .flag(MovementInputFlag.SNEAK_DOWN)
                .build());
        state.velocity(new FloatVector(0.5f, -0.1f, 0.0f));

        MovementCollisionEngine engine = new MovementCollisionEngine();
        engine.move(state, world, 0.0f);

        assertTrue(state.position().x() < 0.5f - EPSILON,
                "sneaking near the edge should have reduced the requested movement");
        assertTrue(state.position().x() - HALF_WIDTH + EDGE_INSET < 0.0f,
                "the reduced movement should have kept the player supported");
    }

    private static final class FakeWorldView implements MovementWorldView {
        private final List<FloatBox> boxes;

        private FakeWorldView(List<FloatBox> boxes) {
            this.boxes = boxes;
        }

        @Override
        public MovementBlockView block(int x, int y, int z) {
            return MovementBlockView.AIR;
        }

        @Override
        public List<FloatBox> collisionBoxes(FloatBox area) {
            return boxes.stream().filter(area::intersects).toList();
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
            return FluidState.NONE;
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
