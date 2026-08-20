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
        /** The simulation restarted from the client this tick, so the offset covers this tick alone. */
        boolean anchored,
        /** What the simulation believes is holding the player up, for diagnosing ground disputes. */
        String supportingBlock,
        int supportingBlockY,
        boolean onGround,
        long tick
) {
    public float offset() {
        return positionDifference.length();
    }
}
