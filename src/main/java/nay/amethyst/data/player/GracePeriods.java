package nay.amethyst.data.player;

import java.util.EnumMap;

final class GracePeriods {
    private final EnumMap<GraceReason, Long> expirations = new EnumMap<>(GraceReason.class);

    synchronized void grant(GraceReason reason, long durationMillis) {
        if (reason == null) {
            throw new IllegalArgumentException("Grace reason cannot be null");
        }
        if (durationMillis <= 0) {
            expirations.remove(reason);
            return;
        }
        long now = System.nanoTime();
        long durationNanos = Math.multiplyExact(durationMillis, 1_000_000L);
        long expiration = durationNanos > Long.MAX_VALUE - now
                ? Long.MAX_VALUE : now + durationNanos;
        expirations.merge(reason, expiration, Math::max);
    }

    synchronized boolean active(GraceReason reason) {
        Long expiration = expirations.get(reason);
        if (expiration == null) {
            return false;
        }
        if (System.nanoTime() < expiration) {
            return true;
        }
        expirations.remove(reason);
        return false;
    }

    synchronized void revoke(GraceReason reason) {
        expirations.remove(reason);
    }

    synchronized void clear() {
        expirations.clear();
    }

}
