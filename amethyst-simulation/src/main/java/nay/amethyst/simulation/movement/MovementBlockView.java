package nay.amethyst.simulation.movement;

/**
 * Movement-relevant view of one block.
 *
 * @param fluid                0 none, 1 water, 2 lava
 * @param fluidDepth           liquid depth (0 source .. 7, 8+ falling), 255 when unknown
 * @param scaffoldingStability scaffolding stability (7 = falls through), -1 when not scaffolding
 * @param bubble               bubble column: -1 drag down, 1 push up, 0 none
 * @param solidMotion          a collision shape that is not fluid, climbable or web (blocks flow)
 * @param fullCube             a single full-cube collision shape
 * @param empty                no collision shape
 */
public record MovementBlockView(String id, float friction, boolean liquid,
                                boolean bamboo, boolean climbable, int fluid, int fluidDepth,
                                int scaffoldingStability, int bubble, boolean solidMotion,
                                boolean fullCube, boolean empty) {
    public static final MovementBlockView AIR = new MovementBlockView(
            "minecraft:air", 0.6f, false, false, false
    );

    public MovementBlockView(String id, float friction, boolean liquid, boolean bamboo,
                             boolean climbable) {
        this(id, friction, liquid, bamboo, climbable,
                !liquid ? 0 : id.contains("lava") ? 2 : 1, 0,
                id.equals("scaffolding") || id.equals("minecraft:scaffolding") ? 0 : -1,
                0, false, false, true);
    }

    public boolean air() {
        return "minecraft:air".equals(id);
    }

    public boolean named(String name) {
        return id.equals(name) || id.equals("minecraft:" + name);
    }

    public boolean preventsJumping() {
        return named("honey") || named("honey_block");
    }
}
