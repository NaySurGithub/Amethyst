package nay.amethyst.simulation.movement;

public record MovementPipelineResult(
        FloatVector clientPosition,
        FloatVector authoritativePosition,
        FloatVector authoritativeVelocity,
        FloatVector forwardedPosition,
        FloatVector positionDifference,
        FloatVector velocityDifference,
        boolean correctionRequired,
        boolean reliable,
        boolean inFluid,
        boolean impulseApplied,
        boolean impulseDeferred,
        long ticksSinceImpulse,
        boolean anchored,
        String supportingBlock,
        int supportingBlockY,
        String blockBelow,
        boolean onGround,
        long tick
) {
    public float offset() {
        return positionDifference.length();
    }
}
