package nay.amethyst.simulation.movement;

public record MovementOptions(
        float correctionThreshold,
        boolean acceptClientVelocity,
        float velocityAcceptanceThreshold
) {
    public static MovementOptions defaults() {
        return new MovementOptions(MovementConstants.CORRECTION_THRESHOLD, false, 0.03f);
    }
}
