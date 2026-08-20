package nay.amethyst.tracking.entity;

import nay.amethyst.prediction.common.Vec3;

public record ClientEntityView(
        long runtimeId,
        Vec3 previousPosition,
        Vec3 position,
        boolean player,
        int ticksSinceTeleport,
        double width,
        double height,
        double scale
) {
}
