package nay.amethyst.simulation.movement;

public record MovementOptions(
        float correctionThreshold,
        boolean acceptClientVelocity,
        float velocityAcceptanceThreshold,
        /** Off by default: the swim model is written but not yet checked. */
        boolean simulateWater
) {
    public static MovementOptions defaults() {
        return new MovementOptions(MovementConstants.CORRECTION_THRESHOLD, false, 0.03f, false);
    }
}
