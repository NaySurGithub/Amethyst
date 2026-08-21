package nay.amethyst.simulation.movement;

/** The fluid a bounding box stands in. {@code flow} is summed and unnormalised. */
public record FluidState(boolean water, boolean lava, float submersion, FloatVector flow,
                         int bubbleDirection, boolean bubbleSurface) {
    public static final FluidState NONE = new FluidState(false, false, 0.0f, FloatVector.ZERO,
            0, false);

    public boolean any() {
        return water || lava;
    }
}
