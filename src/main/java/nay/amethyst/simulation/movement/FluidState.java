package nay.amethyst.simulation.movement;

/**
 * The fluid a bounding box is standing in, and the push it receives from it. {@code flow} is the
 * summed flow of every fluid block touching the box, left unnormalised so the caller decides the
 * push strength.
 */
public record FluidState(boolean water, boolean lava, float submersion, FloatVector flow,
                         int bubbleDirection, boolean bubbleSurface) {
    public static final FluidState NONE = new FluidState(false, false, 0.0f, FloatVector.ZERO,
            0, false);

    public boolean any() {
        return water || lava;
    }
}
