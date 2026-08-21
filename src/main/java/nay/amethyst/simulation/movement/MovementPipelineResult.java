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
        /** Ticks since the last impulse reached the simulation, to tell "late" from "never". */
        long ticksSinceImpulse,
        /** The simulation restarted from the client this tick, so the offset covers this tick alone. */
        boolean anchored,
        /** What the simulation believes is holding the player up, for diagnosing ground disputes. */
        String supportingBlock,
        int supportingBlockY,
        /** What the captured frame holds just under the client, to tell a missing block from a bad fall. */
        String blockBelow,
        boolean onGround,
        long tick
) {
    public float offset() {
        return positionDifference.length();
    }
}
