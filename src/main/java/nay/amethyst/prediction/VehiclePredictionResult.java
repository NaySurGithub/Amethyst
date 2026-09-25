package nay.amethyst.prediction;

import org.powernukkitx.math.Vector3;

public record VehiclePredictionResult(
        String type,
        double offset,
        Vector3 movement,
        Vector3 velocity,
        boolean horizontalCollision,
        boolean verticalCollision
) {
}
