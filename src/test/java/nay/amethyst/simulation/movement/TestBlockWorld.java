package nay.amethyst.simulation.movement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A world built one block at a time, so a test states only the blocks it cares about. */
final class TestBlockWorld implements MovementWorldView {
    static final MovementBlockView STONE =
            new MovementBlockView("minecraft:stone", 0.6f, false, false, false);
    static final MovementBlockView POWDER_SNOW =
            new MovementBlockView("minecraft:powder_snow", 0.6f, false, false, false);
    static final MovementBlockView COBWEB =
            new MovementBlockView("minecraft:web", 0.6f, false, false, false);
    static final MovementBlockView SWEET_BERRY_BUSH =
            new MovementBlockView("minecraft:sweet_berry_bush", 0.6f, false, false, false);
    static final MovementBlockView SOUL_SAND =
            new MovementBlockView("minecraft:soul_sand", 0.6f, false, false, false);
    static final MovementBlockView HONEY =
            new MovementBlockView("minecraft:honey_block", 0.8f, false, false, false);
    static final MovementBlockView SLIME =
            new MovementBlockView("minecraft:slime", 0.8f, false, false, false);
    static final MovementBlockView BED =
            new MovementBlockView("minecraft:bed", 0.6f, false, false, false);
    static final MovementBlockView ICE =
            new MovementBlockView("minecraft:ice", 0.98f, false, false, false);
    static final MovementBlockView LADDER =
            new MovementBlockView("minecraft:ladder", 0.6f, false, false, true);

    private final Map<Long, MovementBlockView> blocks = new HashMap<>();
    private final List<FloatBox> collisions = new ArrayList<>();

    TestBlockWorld put(int x, int y, int z, MovementBlockView view) {
        blocks.put(key(x, y, z), view);
        return this;
    }

    TestBlockWorld solid(int x, int y, int z, MovementBlockView view) {
        put(x, y, z, view);
        collisions.add(new FloatBox(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f));
        return this;
    }

    TestBlockWorld floor(int y, MovementBlockView view) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                solid(x, y, z, view);
            }
        }
        return this;
    }

    /** Fills the column a standing player occupies, plus a block of slack in every direction. */
    TestBlockWorld fill(int minY, int maxY, MovementBlockView view) {
        for (int x = -2; x <= 2; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -2; z <= 2; z++) {
                    put(x, y, z, view);
                }
            }
        }
        return this;
    }

    @Override
    public MovementBlockView block(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), MovementBlockView.AIR);
    }

    @Override
    public List<FloatBox> collisionBoxes(FloatBox area) {
        List<FloatBox> hits = new ArrayList<>();
        for (FloatBox box : collisions) {
            if (area.intersects(box)) {
                hits.add(box);
            }
        }
        return hits;
    }

    @Override
    public boolean contains(FloatBox area) {
        return true;
    }

    @Override
    public boolean hasLiquidIntersection(FloatBox area) {
        return false;
    }

    @Override
    public FluidState fluidState(FloatBox area) {
        return FluidState.NONE;
    }

    @Override
    public float underwaterSpeed() {
        return 0.0f;
    }

    @Override
    public boolean hasBambooNearby(FloatBox area) {
        return false;
    }

    @Override
    public boolean hasScaffoldingIntersection(FloatBox area) {
        return false;
    }

    @Override
    public boolean hasMovingBlock(FloatBox area) {
        return false;
    }

    @Override
    public boolean hasSolidEntityNearby(FloatBox area) {
        return false;
    }

    @Override
    public boolean hasSolidEntityIntersecting(FloatBox area) {
        return false;
    }

    @Override
    public MovementBlockPosition supportingBlock(FloatBox area, FloatVector playerPosition) {
        return null;
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | ((long) z & 0xFFFFF);
    }
}
