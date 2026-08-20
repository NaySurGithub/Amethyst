package nay.amethyst.simulation.movement;

public record FloatVector(float x, float y, float z) {
    public static final FloatVector ZERO = new FloatVector(0.0f, 0.0f, 0.0f);

    public FloatVector add(FloatVector other) {
        return new FloatVector(x + other.x, y + other.y, z + other.z);
    }

    public FloatVector add(float addX, float addY, float addZ) {
        return new FloatVector(x + addX, y + addY, z + addZ);
    }

    public FloatVector subtract(FloatVector other) {
        return new FloatVector(x - other.x, y - other.y, z - other.z);
    }

    public FloatVector multiply(float multiplier) {
        return new FloatVector(x * multiplier, y * multiplier, z * multiplier);
    }

    public FloatVector multiply(float multiplierX, float multiplierY, float multiplierZ) {
        return new FloatVector(x * multiplierX, y * multiplierY, z * multiplierZ);
    }

    public float lengthSquared() {
        return x * x + y * y + z * z;
    }

    public float length() {
        return (float) Math.sqrt(lengthSquared());
    }

    public float horizontalLengthSquared() {
        return x * x + z * z;
    }

    public static FloatVector minimum(FloatVector first, FloatVector second) {
        return new FloatVector(Math.min(first.x, second.x), Math.min(first.y, second.y),
                Math.min(first.z, second.z));
    }

    public static FloatVector maximum(FloatVector first, FloatVector second) {
        return new FloatVector(Math.max(first.x, second.x), Math.max(first.y, second.y),
                Math.max(first.z, second.z));
    }
}
