package nay.amethyst.data.player;

import nay.amethyst.prediction.common.Vec3;

public record VelocityImpulse(long sequence, long sentInputSequence, long sentNanos, Vec3 velocity) {
}
