package nay.amethyst.simulation.movement;

public record MovementBlockView(String id, float friction, boolean liquid,
                                boolean bamboo, boolean climbable) {
    public static final MovementBlockView AIR = new MovementBlockView(
            "minecraft:air", 0.6f, false, false, false
    );

    public boolean air() {
        return "minecraft:air".equals(id);
    }

    public boolean named(String name) {
        return id.equals(name) || id.equals("minecraft:" + name);
    }
}
