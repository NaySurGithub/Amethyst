package nay.amethyst.network;

/** Which simulated ticks may serve as a setback target. */
final class SetbackTargets {
    private SetbackTargets() {
    }

    /**
     * A reliable tick whose offset stayed within tolerance with no pending excess in the buffer,
     * on the ground, in the air or in a fluid alike.
     */
    static boolean accepted(double buffer, double excess, boolean reliable) {
        return reliable && buffer <= 0.0 && excess <= 0.0;
    }
}
