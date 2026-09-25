package nay.amethyst.prediction;

public record CombatPredictionResult(
        boolean valid,
        boolean raycastHit,
        double rayDistance,
        double rawDistance,
        double angle
) {
}
