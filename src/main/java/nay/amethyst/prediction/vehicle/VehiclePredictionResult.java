package nay.amethyst.prediction.vehicle;

import nay.amethyst.prediction.common.Vec3;

public record VehiclePredictionResult(
        String type,
        double offset,
        Vec3 movement,
        Vec3 velocity,
        boolean horizontalCollision,
        boolean verticalCollision
) {
}
