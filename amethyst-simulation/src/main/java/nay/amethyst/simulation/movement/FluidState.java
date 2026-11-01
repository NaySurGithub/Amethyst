package nay.amethyst.simulation.movement;

/** The fluid a bounding box stands in (BDS liquid query bounds; lava is selected first). */
public record FluidState(boolean water, boolean lava, float submersion) {
    public static final FluidState NONE = new FluidState(false, false, 0.0f);

    public boolean any() {
        return water || lava;
    }
}
