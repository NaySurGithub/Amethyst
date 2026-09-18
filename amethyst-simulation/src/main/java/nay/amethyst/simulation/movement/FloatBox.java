package nay.amethyst.simulation.movement;

public record FloatBox(float minX, float minY, float minZ,
                       float maxX, float maxY, float maxZ) {
    public FloatBox offset(FloatVector vector) {
        return new FloatBox(minX + vector.x(), minY + vector.y(), minZ + vector.z(),
                maxX + vector.x(), maxY + vector.y(), maxZ + vector.z());
    }

    public FloatBox grow(float x, float y, float z) {
        return new FloatBox(minX - x, minY - y, minZ - z,
                maxX + x, maxY + y, maxZ + z);
    }

    public FloatBox extend(FloatVector vector) {
        return new FloatBox(
                vector.x() < 0.0f ? minX + vector.x() : minX,
                vector.y() < 0.0f ? minY + vector.y() : minY,
                vector.z() < 0.0f ? minZ + vector.z() : minZ,
                vector.x() > 0.0f ? maxX + vector.x() : maxX,
                vector.y() > 0.0f ? maxY + vector.y() : maxY,
                vector.z() > 0.0f ? maxZ + vector.z() : maxZ
        );
    }

    public boolean intersects(FloatBox other) {
        return maxX > other.minX && minX < other.maxX
                && maxY > other.minY && minY < other.maxY
                && maxZ > other.minZ && minZ < other.maxZ;
    }

    public FloatVector feetPosition() {
        return new FloatVector((minX + maxX) * 0.5f, minY, (minZ + maxZ) * 0.5f);
    }
}
