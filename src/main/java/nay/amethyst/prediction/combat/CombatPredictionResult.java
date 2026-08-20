package nay.amethyst.prediction.combat;

public record CombatPredictionResult(
        boolean valid,
        boolean raycastHit,
        double rayDistance,
        double rawDistance,
        double angle
) {
}
