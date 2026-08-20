package nay.amethyst.prediction.common;

public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);

    public Vec3 add(Vec3 other) {
        return new Vec3(x + other.x, y + other.y, z + other.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 multiply(double xFactor, double yFactor, double zFactor) {
        return new Vec3(x * xFactor, y * yFactor, z * zFactor);
    }

    public double distance(Vec3 other) {
        return Math.sqrt(distanceSquared(other));
    }

    public double distanceSquared(Vec3 other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    public double horizontalLength() {
        return Math.hypot(x, z);
    }

    public double lengthSquared() {
        return x * x + y * y + z * z;
    }
}
