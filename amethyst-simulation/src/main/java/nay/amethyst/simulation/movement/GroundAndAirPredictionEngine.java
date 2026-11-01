package nay.amethyst.simulation.movement;

/** Walking, falling and jumping, driven by block friction (Bedrock 1.26.52 / BDS order). */
public final class GroundAndAirPredictionEngine extends PredictionEngine {

    public GroundAndAirPredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                        MovementCollisionEngine collisions,
                                        float correctionThreshold) {
        super(state, world, collisions, correctionThreshold);
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        return travel(Medium.GROUND);
    }
}
