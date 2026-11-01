package nay.amethyst.simulation.movement;

/** Water travel: water acceleration and drag, swim control, 0.04 lift and -0.005 gravity. */
public final class WaterPredictionEngine extends PredictionEngine {

    static final float ACCELERATION = 0.02f;

    public WaterPredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                 MovementCollisionEngine collisions, float correctionThreshold,
                                 FluidState fluid) {
        super(state, world, collisions, correctionThreshold);
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        return travel(Medium.WATER);
    }
}
