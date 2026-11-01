package nay.amethyst.simulation.movement;

/** Lava travel: lava_movement acceleration, 0.5 drag and -0.02 gravity. */
public final class LavaPredictionEngine extends PredictionEngine {

    public LavaPredictionEngine(AuthoritativeMotionState state, MovementWorldView world,
                                MovementCollisionEngine collisions, float correctionThreshold,
                                FluidState fluid) {
        super(state, world, collisions, correctionThreshold);
    }

    @Override
    public MovementSimulator.SimulationResult run() {
        return travel(Medium.LAVA);
    }
}
