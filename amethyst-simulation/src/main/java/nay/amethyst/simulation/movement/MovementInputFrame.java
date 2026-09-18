package nay.amethyst.simulation.movement;

import java.util.EnumSet;
import java.util.Set;

public record MovementInputFrame(
        long tick,
        FloatVector position,
        FloatVector delta,
        FloatVector rotation,
        float moveSideways,
        float moveForward,
        Set<MovementInputFlag> flags
) {
    public MovementInputFrame {
        flags = flags.isEmpty() ? Set.of() : Set.copyOf(flags);
    }

    public boolean has(MovementInputFlag flag) {
        return flags.contains(flag);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long tick;
        private FloatVector position = FloatVector.ZERO;
        private FloatVector delta = FloatVector.ZERO;
        private FloatVector rotation = FloatVector.ZERO;
        private float moveSideways;
        private float moveForward;
        private final EnumSet<MovementInputFlag> flags = EnumSet.noneOf(MovementInputFlag.class);

        public Builder tick(long tick) {
            this.tick = tick;
            return this;
        }

        public Builder position(FloatVector position) {
            this.position = position;
            return this;
        }

        public Builder delta(FloatVector delta) {
            this.delta = delta;
            return this;
        }

        public Builder rotation(FloatVector rotation) {
            this.rotation = rotation;
            return this;
        }

        public Builder moveVector(float sideways, float forward) {
            this.moveSideways = sideways;
            this.moveForward = forward;
            return this;
        }

        public Builder flag(MovementInputFlag flag) {
            flags.add(flag);
            return this;
        }

        public MovementInputFrame build() {
            return new MovementInputFrame(tick, position, delta, rotation,
                    moveSideways, moveForward, flags);
        }
    }
}
