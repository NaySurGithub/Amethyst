package nay.amethyst.player;

import org.powernukkitx.math.Vector3;

public record VelocityImpulse(long sequence, long sentInputSequence, long sentNanos, Vector3 velocity) {
}
