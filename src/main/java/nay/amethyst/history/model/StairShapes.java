package nay.amethyst.history.model;

import org.powernukkitx.math.BlockFace;

import java.util.ArrayList;
import java.util.List;

/** Derives a stair's shape from its neighbours and rebuilds its collision boxes. */
public final class StairShapes {

    public interface Neighbours {
        BlockFrame at(int x, int y, int z);
    }

    private enum Shape {
        STRAIGHT, INNER_LEFT, INNER_RIGHT, OUTER_LEFT, OUTER_RIGHT
    }

    private StairShapes() {
    }

    public static List<Aabb> collisions(int x, int y, int z, StairFrame stair, Neighbours neighbours) {
        BlockFace facing = stair.facing();
        double slabMinY = stair.upsideDown() ? 0.5 : 0.0;
        double stepMinY = stair.upsideDown() ? 0.0 : 0.5;
        double stepMaxY = stepMinY + 0.5;

        List<Aabb> boxes = new ArrayList<>(3);
        boxes.add(new Aabb(x, y + slabMinY, z, x + 1, y + slabMinY + 0.5, z + 1));

        switch (shapeOf(x, y, z, stair, neighbours)) {
            case STRAIGHT -> boxes.add(half(x, y, z, facing, stepMinY, stepMaxY));
            case OUTER_LEFT -> boxes.add(quarter(x, y, z, facing, facing.rotateYCCW(), stepMinY, stepMaxY));
            case OUTER_RIGHT -> boxes.add(quarter(x, y, z, facing, facing.rotateY(), stepMinY, stepMaxY));
            case INNER_LEFT -> {
                boxes.add(half(x, y, z, facing, stepMinY, stepMaxY));
                boxes.add(quarter(x, y, z, facing.getOpposite(), facing.rotateYCCW(), stepMinY, stepMaxY));
            }
            case INNER_RIGHT -> {
                boxes.add(half(x, y, z, facing, stepMinY, stepMaxY));
                boxes.add(quarter(x, y, z, facing.getOpposite(), facing.rotateY(), stepMinY, stepMaxY));
            }
        }
        return boxes;
    }

    private static Shape shapeOf(int x, int y, int z, StairFrame stair, Neighbours neighbours) {
        BlockFace facing = stair.facing();

        StairFrame front = stairAt(neighbours, x, y, z, facing);
        if (front != null && front.upsideDown() == stair.upsideDown()
                && front.facing().getAxis() != facing.getAxis()
                && canTakeShape(x, y, z, stair, neighbours, front.facing().getOpposite())) {
            return front.facing() == facing.rotateYCCW() ? Shape.OUTER_LEFT : Shape.OUTER_RIGHT;
        }

        StairFrame back = stairAt(neighbours, x, y, z, facing.getOpposite());
        if (back != null && back.upsideDown() == stair.upsideDown()
                && back.facing().getAxis() != facing.getAxis()
                && canTakeShape(x, y, z, stair, neighbours, back.facing())) {
            return back.facing() == facing.rotateYCCW() ? Shape.INNER_LEFT : Shape.INNER_RIGHT;
        }

        return Shape.STRAIGHT;
    }

    private static boolean canTakeShape(int x, int y, int z, StairFrame stair,
                                        Neighbours neighbours, BlockFace side) {
        StairFrame other = stairAt(neighbours, x, y, z, side);
        return other == null || other.facing() != stair.facing()
                || other.upsideDown() != stair.upsideDown();
    }

    private static StairFrame stairAt(Neighbours neighbours, int x, int y, int z, BlockFace face) {
        BlockFrame frame = neighbours.at(x + face.getXOffset(), y + face.getYOffset(),
                z + face.getZOffset());
        return frame == null ? null : frame.stair();
    }

    private static Aabb half(int x, int y, int z, BlockFace face, double minY, double maxY) {
        double minX = x;
        double maxX = x + 1;
        double minZ = z;
        double maxZ = z + 1;
        switch (face) {
            case NORTH -> maxZ = z + 0.5;
            case SOUTH -> minZ = z + 0.5;
            case WEST -> maxX = x + 0.5;
            case EAST -> minX = x + 0.5;
            default -> {
            }
        }
        return new Aabb(minX, y + minY, minZ, maxX, y + maxY, maxZ);
    }

    private static Aabb quarter(int x, int y, int z, BlockFace first, BlockFace second,
                                double minY, double maxY) {
        Aabb a = half(x, y, z, first, minY, maxY);
        Aabb b = half(x, y, z, second, minY, maxY);
        return new Aabb(Math.max(a.minX(), b.minX()), y + minY, Math.max(a.minZ(), b.minZ()),
                Math.min(a.maxX(), b.maxX()), y + maxY, Math.min(a.maxZ(), b.maxZ()));
    }
}
