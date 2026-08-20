package nay.amethyst.tracking.entity;

import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.history.model.Aabb;
import nay.amethyst.history.model.EntityFrame;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataMap;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ClientEntityTracker {
    private static final double PLAYER_HEIGHT_OFFSET = 1.62;

    private final Map<Long, TrackedEntity> entities = new HashMap<>();
    private final List<Update> queuedUpdates = new ArrayList<>();
    private boolean flushScheduled;

    /**
     * The types a player can stand on. Every other entity is walked through, so treating them all as
     * solid would be worse than ignoring them.
     */
    private static final Set<String> SOLID_TYPES = Set.of(
            "minecraft:boat", "minecraft:chest_boat", "minecraft:minecart",
            "minecraft:chest_minecart", "minecraft:hopper_minecart", "minecraft:tnt_minecart",
            "minecraft:command_block_minecart", "minecraft:shulker");

    public synchronized void add(long runtimeId, Vector3f position, boolean player,
                                 ActorDataMap actorData, String identifier) {
        if (position == null) return;
        Vec3 converted = vector(position);
        entities.put(runtimeId, new TrackedEntity(runtimeId, converted, player,
                value(actorData, ActorDataTypes.WIDTH, 0.6f),
                value(actorData, ActorDataTypes.HEIGHT, 1.8f),
                value(actorData, ActorDataTypes.SCALE, 1.0f),
                identifier != null && SOLID_TYPES.contains(identifier)));
    }

    public synchronized boolean queueAdd(long runtimeId, Vector3f position, boolean player,
                                         ActorDataMap actorData, String identifier) {
        if (position == null) return false;
        queuedUpdates.add(new SpawnUpdate(runtimeId, position, player, actorData, identifier));
        if (flushScheduled) return false;
        flushScheduled = true;
        return true;
    }

    public synchronized boolean queueRemove(long runtimeId) {
        queuedUpdates.add(new RemoveUpdate(runtimeId));
        if (flushScheduled) return false;
        flushScheduled = true;
        return true;
    }

    public synchronized void remove(long runtimeId) {
        entities.remove(runtimeId);
        queuedUpdates.removeIf(update -> update.runtimeId() == runtimeId);
    }

    public synchronized boolean queueAbsolute(long runtimeId, Vector3f position, boolean teleport) {
        TrackedEntity entity = entities.get(runtimeId);
        if (entity == null || position == null) return false;
        Vec3 converted = vector(position);
        if (entity.player) converted = converted.add(0, -PLAYER_HEIGHT_OFFSET, 0);
        entity.serverPosition = converted;
        queuedUpdates.add(new PositionUpdate(runtimeId, converted, teleport));
        if (flushScheduled) return false;
        flushScheduled = true;
        return true;
    }

    public synchronized boolean queueSize(long runtimeId, ActorDataMap actorData) {
        TrackedEntity entity = entities.get(runtimeId);
        if (entity == null || actorData == null) return false;
        Float width = actorData.get(ActorDataTypes.WIDTH);
        Float height = actorData.get(ActorDataTypes.HEIGHT);
        Float scale = actorData.get(ActorDataTypes.SCALE);
        if (width == null && height == null && scale == null) return false;
        queuedUpdates.add(new SizeUpdate(runtimeId, width, height, scale));
        if (flushScheduled) return false;
        flushScheduled = true;
        return true;
    }

    public synchronized boolean queueDelta(long runtimeId, Float x, Float y, Float z, boolean teleport) {
        TrackedEntity entity = entities.get(runtimeId);
        if (entity == null) return false;
        Vec3 previous = entity.serverPosition;
        Vec3 position = new Vec3(x == null ? previous.x() : x,
                y == null ? previous.y() : y, z == null ? previous.z() : z);
        return queueAbsolute(runtimeId, Vector3f.from(position.x(), position.y(), position.z()), teleport);
    }

    public synchronized List<Update> drainQueuedUpdates() {
        flushScheduled = false;
        if (queuedUpdates.isEmpty()) return List.of();
        List<Update> batch = List.copyOf(queuedUpdates);
        queuedUpdates.clear();
        return batch;
    }

    public synchronized void acknowledge(List<Update> updates) {
        for (Update update : updates) {
            if (update instanceof SpawnUpdate spawn) {
                add(spawn.runtimeId, spawn.position, spawn.player, spawn.actorData, spawn.identifier);
                continue;
            }
            if (update instanceof RemoveUpdate remove) {
                remove(remove.runtimeId);
                continue;
            }
            TrackedEntity entity = entities.get(update.runtimeId());
            if (entity == null) continue;
            if (update instanceof PositionUpdate position) {
                entity.receive(position.position, position.teleport);
            } else if (update instanceof SizeUpdate size) {
                if (size.width != null) entity.width = size.width;
                if (size.height != null) entity.height = size.height;
                if (size.scale != null) entity.scale = size.scale;
            }
        }
    }

    public synchronized void tick() {
        for (TrackedEntity entity : entities.values()) entity.tick();
    }

    public synchronized ClientEntityView view(long runtimeId) {
        TrackedEntity entity = entities.get(runtimeId);
        return entity == null ? null : new ClientEntityView(runtimeId, entity.previousPosition,
                entity.position, entity.player, entity.ticksSinceTeleport,
                entity.width, entity.height, entity.scale);
    }

    public synchronized Map<Long, EntityFrame> snapshotFrames(Vec3 center, double radius) {
        double radiusSquared = radius * radius;
        Map<Long, EntityFrame> snapshot = new HashMap<>();
        for (TrackedEntity entity : entities.values()) {
            if (entity.position == null || entity.position.add(-center.x(), -center.y(), -center.z()).lengthSquared()
                    > radiusSquared) continue;
            double halfWidth = entity.width * entity.scale / 2.0;
            double height = entity.height * entity.scale;
            snapshot.put(entity.runtimeId, new EntityFrame(entity.runtimeId,
                    new Aabb(entity.position.x() - halfWidth, entity.position.y(),
                            entity.position.z() - halfWidth, entity.position.x() + halfWidth,
                            entity.position.y() + height, entity.position.z() + halfWidth),
                    entity.solid));
        }
        return snapshot;
    }

    public synchronized int retainedEntityCount() {
        return entities.size() + queuedUpdates.size();
    }

    public synchronized void clear() {
        entities.clear();
        queuedUpdates.clear();
        flushScheduled = false;
    }

    private static Vec3 vector(Vector3f position) {
        return new Vec3(position.getX(), position.getY(), position.getZ());
    }

    private static float value(ActorDataMap data,
                               org.cloudburstmc.protocol.bedrock.data.actor.ActorDataType<Float> type,
                               float fallback) {
        if (data == null) return fallback;
        Float value = data.get(type);
        return value == null ? fallback : value;
    }

    public sealed interface Update permits SpawnUpdate, RemoveUpdate, PositionUpdate, SizeUpdate {
        long runtimeId();
    }

    public record SpawnUpdate(long runtimeId, Vector3f position, boolean player,
                              ActorDataMap actorData, String identifier) implements Update {
    }

    public record RemoveUpdate(long runtimeId) implements Update {
    }

    public record PositionUpdate(long runtimeId, Vec3 position, boolean teleport) implements Update {
    }

    public record SizeUpdate(long runtimeId, Float width, Float height, Float scale) implements Update {
    }

    private static final class TrackedEntity {
        private final long runtimeId;
        private final boolean player;
        private Vec3 previousPosition;
        private Vec3 position;
        private Vec3 receivedPosition;
        private Vec3 serverPosition;
        private int interpolationTicks;
        private int ticksSinceTeleport;
        private double width;
        private double height;
        private double scale;
        private final boolean solid;

        private TrackedEntity(long runtimeId, Vec3 position, boolean player,
                              double width, double height, double scale, boolean solid) {
            this.runtimeId = runtimeId;
            this.player = player;
            this.previousPosition = position;
            this.position = position;
            this.receivedPosition = position;
            this.serverPosition = position;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.solid = solid;
        }

        private void receive(Vec3 received, boolean teleport) {
            receivedPosition = received;
            interpolationTicks = teleport ? 1 : player ? 3 : 6;
            if (teleport) ticksSinceTeleport = 0;
        }

        private void tick() {
            previousPosition = position;
            if (interpolationTicks > 0) {
                double scale = 1.0 / interpolationTicks;
                position = position.add((receivedPosition.x() - position.x()) * scale,
                        (receivedPosition.y() - position.y()) * scale,
                        (receivedPosition.z() - position.z()) * scale);
                interpolationTicks--;
            } else {
                position = receivedPosition;
            }
            ticksSinceTeleport++;
        }
    }
}
