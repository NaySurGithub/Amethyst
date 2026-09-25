package nay.amethyst.world;

import org.powernukkitx.math.Vector3;

public record ClientEntityView(
        long runtimeId,
        Vector3 previousPosition,
        Vector3 position,
        boolean player,
        int ticksSinceTeleport,
        double width,
        double height,
        double scale
) {
}
