package nay.amethyst.history.model;

import nay.amethyst.prediction.common.Vec3;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record WorldFrame(
        long clientTick,
        long capturedNanos,
        String levelName,
        Vec3 position,
        Vec3 velocity,
        float yaw,
        float pitch,
        boolean onGround,
        PhysicsFrame physics,
        Aabb capturedArea,
        Map<BlockPos, BlockFrame> blocks,
        BlockIndex index,
        Map<Long, EntityFrame> entities,
        /** Boxes of the entities a player can stand on, flattened so a query walks a list, not a map. */
        List<Aabb> solidEntityBoxes,
        List<Aabb> collisions
) {
    public WorldFrame {
        blocks = Collections.unmodifiableMap(blocks);
        entities = Collections.unmodifiableMap(entities);
        solidEntityBoxes = List.copyOf(solidEntityBoxes);
        collisions = Collections.unmodifiableList(collisions);
    }

    public static List<Aabb> solidBoxesOf(Map<Long, EntityFrame> entities) {
        List<Aabb> boxes = null;
        for (EntityFrame entity : entities.values()) {
            if (!entity.solid()) {
                continue;
            }
            if (boxes == null) {
                boxes = new ArrayList<>(2);
            }
            boxes.add(entity.box());
        }
        return boxes == null ? List.of() : boxes;
    }

    public BlockFrame blockAt(int x, int y, int z) {
        return blocks.get(new BlockPos(x, y, z));
    }

    public boolean covers(Aabb area) {
        return capturedArea.contains(area);
    }

    public boolean coversBlock(int x, int y, int z) {
        return x >= capturedArea.minX() && x < capturedArea.maxX()
                && y >= capturedArea.minY() && y < capturedArea.maxY()
                && z >= capturedArea.minZ() && z < capturedArea.maxZ();
    }

    public List<Aabb> collisionBoxes(Aabb area) {
        List<Aabb> nearby = new ArrayList<>(Math.min(collisions.size(), 64));
        for (Aabb collision : collisions) {
            if (collision.intersects(area)) nearby.add(collision);
        }
        return nearby;
    }
}
