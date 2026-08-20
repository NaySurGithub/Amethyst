package nay.amethyst.history.model;

import nay.amethyst.prediction.common.Vec3;
import org.powernukkitx.math.AxisAlignedBB;

public record Aabb(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    private static final double EPSILON = 1.0E-7;

    public static Aabb from(AxisAlignedBB box) {
        return new Aabb(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    public Aabb offset(double x, double y, double z) {
        return new Aabb(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    public Aabb stretch(Vec3 movement) {
        return new Aabb(
                movement.x() < 0 ? minX + movement.x() : minX,
                movement.y() < 0 ? minY + movement.y() : minY,
                movement.z() < 0 ? minZ + movement.z() : minZ,
                movement.x() > 0 ? maxX + movement.x() : maxX,
                movement.y() > 0 ? maxY + movement.y() : maxY,
                movement.z() > 0 ? maxZ + movement.z() : maxZ);
    }

    public Aabb expand(double amount) {
        return new Aabb(minX - amount, minY - amount, minZ - amount,
                maxX + amount, maxY + amount, maxZ + amount);
    }

    public boolean intersects(Aabb other) {
        return other.maxX > minX && other.minX < maxX
                && other.maxY > minY && other.minY < maxY
                && other.maxZ > minZ && other.minZ < maxZ;
    }

    public boolean contains(Aabb other) {
        return other.minX >= minX && other.maxX <= maxX
                && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public double calculateXOffset(Aabb moving, double offset) {
        if (moving.maxY <= minY || moving.minY >= maxY || moving.maxZ <= minZ || moving.minZ >= maxZ) return offset;
        if (offset > 0 && moving.maxX <= minX) offset = Math.min(offset, minX - moving.maxX);
        else if (offset < 0 && moving.minX >= maxX) offset = Math.max(offset, maxX - moving.minX);
        return Math.abs(offset) < EPSILON ? 0 : offset;
    }

    public double calculateYOffset(Aabb moving, double offset) {
        if (moving.maxX <= minX || moving.minX >= maxX || moving.maxZ <= minZ || moving.minZ >= maxZ) return offset;
        if (offset > 0 && moving.maxY <= minY) offset = Math.min(offset, minY - moving.maxY);
        else if (offset < 0 && moving.minY >= maxY) offset = Math.max(offset, maxY - moving.minY);
        return Math.abs(offset) < EPSILON ? 0 : offset;
    }

    public double calculateZOffset(Aabb moving, double offset) {
        if (moving.maxX <= minX || moving.minX >= maxX || moving.maxY <= minY || moving.minY >= maxY) return offset;
        if (offset > 0 && moving.maxZ <= minZ) offset = Math.min(offset, minZ - moving.maxZ);
        else if (offset < 0 && moving.minZ >= maxZ) offset = Math.max(offset, maxZ - moving.minZ);
        return Math.abs(offset) < EPSILON ? 0 : offset;
    }

    public double distanceTo(double x, double y, double z) {
        double dx = Math.max(minX - x, Math.max(0, x - maxX));
        double dy = Math.max(minY - y, Math.max(0, y - maxY));
        double dz = Math.max(minZ - z, Math.max(0, z - maxZ));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public double rayDistance(Vec3 origin, Vec3 direction, double maximum) {
        double near = 0;
        double far = maximum;
        double[] origins = {origin.x(), origin.y(), origin.z()};
        double[] directions = {direction.x(), direction.y(), direction.z()};
        double[] minima = {minX, minY, minZ};
        double[] maxima = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < EPSILON) {
                if (origins[axis] < minima[axis] || origins[axis] > maxima[axis]) return Double.POSITIVE_INFINITY;
                continue;
            }
            double first = (minima[axis] - origins[axis]) / directions[axis];
            double second = (maxima[axis] - origins[axis]) / directions[axis];
            if (first > second) {
                double swap = first;
                first = second;
                second = swap;
            }
            near = Math.max(near, first);
            far = Math.min(far, second);
            if (near > far) return Double.POSITIVE_INFINITY;
        }
        return near <= maximum ? near : Double.POSITIVE_INFINITY;
    }
}
