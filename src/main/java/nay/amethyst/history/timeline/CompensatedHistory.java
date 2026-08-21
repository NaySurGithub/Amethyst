package nay.amethyst.history.timeline;

import nay.amethyst.history.model.Aabb;
import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.history.model.BlockIndex;
import nay.amethyst.history.model.BlockPos;
import nay.amethyst.history.model.EntityFrame;
import nay.amethyst.history.model.PhysicsFrame;
import nay.amethyst.history.model.StairShapes;
import nay.amethyst.history.model.WorldFrame;
import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.tracking.world.ClientWorldTracker;
import nay.amethyst.tracking.entity.ClientEntityTracker;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.entity.Attribute;
import org.powernukkitx.entity.effect.Effect;
import org.powernukkitx.entity.effect.EffectType;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.ItemID;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.math.Vector3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.Collections;

public final class CompensatedHistory {
    private static final int MAX_FRAMES = 20;
    private static final int BLOCK_RADIUS = 4;
    private static final int ENTITY_RADIUS = 16;
    private final NavigableMap<Long, WorldFrame> frames = new TreeMap<>();
    private String cachedLevel;
    private int cachedX = Integer.MIN_VALUE;
    private int cachedY = Integer.MIN_VALUE;
    private int cachedZ = Integer.MIN_VALUE;
    private long cachedWorldRevision = Long.MIN_VALUE;
    private Aabb cachedArea;
    private Map<BlockPos, BlockFrame> cachedBlocks = Map.of();
    private BlockIndex cachedIndex = BlockIndex.EMPTY;
    private List<Aabb> cachedCollisions = List.of();

    public synchronized void capture(Player player, long clientTick, Vec3 position, Vec3 velocity,
                                     float yaw, float pitch, boolean onGround, ClientWorldTracker clientWorld,
                                     ClientEntityTracker clientEntities) {
        double feet = position.y() - player.getBaseOffset();
        int centerX = floor(position.x());
        int centerY = floor(feet);
        int centerZ = floor(position.z());
        long worldRevision = clientWorld.revision();
        String levelName = player.getLevel().getName();
        boolean areaChanged = !levelName.equals(cachedLevel) || centerX != cachedX || centerY != cachedY
                || centerZ != cachedZ || cachedArea == null;
        if (areaChanged) {
            rebuildWorldSnapshot(player, clientWorld, levelName, centerX, centerY, centerZ, worldRevision);
            clientWorld.drainAcknowledgedChanges();
        } else if (worldRevision != cachedWorldRevision) {
            applyWorldChanges(player, clientWorld, clientWorld.drainAcknowledgedChanges(), worldRevision);
        }

        Map<Long, EntityFrame> entities = clientEntities.snapshotFrames(position, ENTITY_RADIUS);

        PhysicsFrame physics = new PhysicsFrame(player.getMovementSpeed(), attribute(player,
                Attribute.UNDER_WATER_MOVEMENT_SPEED, player.getDefaultUnderWaterSpeed()), attribute(player,
                Attribute.LAVA_MOVEMENT_SPEED, player.getDefaultLavaMovementSpeed()),
                knockbackResistance(player), player.getGravity(),
                amplifier(player, EffectType.JUMP_BOOST), amplifier(player, EffectType.LEVITATION),
                player.hasEffect(EffectType.SLOW_FALLING), player.isSprinting(), player.isSwimming(),
                player.isGliding(), player.getWidth(), player.getHeight(), player.getScale(), player.getBaseOffset(),
                player.getEyeHeight());
        WorldFrame frame = new WorldFrame(clientTick, System.nanoTime(), levelName, position,
                velocity, yaw, pitch, onGround, physics, cachedArea, cachedBlocks, cachedIndex,
                entities, WorldFrame.solidBoxesOf(entities), cachedCollisions);
        frames.put(clientTick, frame);
        while (frames.size() > MAX_FRAMES) frames.pollFirstEntry();
    }

    public synchronized WorldFrame atOrBefore(long clientTick) {
        Map.Entry<Long, WorldFrame> entry = frames.floorEntry(clientTick);
        return entry == null ? null : entry.getValue();
    }

    public synchronized WorldFrame latest() {
        return frames.isEmpty() ? null : frames.lastEntry().getValue();
    }

    public synchronized List<WorldFrame> recent(int count) {
        if (count <= 0 || frames.isEmpty()) return List.of();
        return frames.descendingMap().values().stream().limit(count).toList();
    }

    public synchronized int size() {
        return frames.size();
    }

    public synchronized void clear() {
        frames.clear();
        cachedLevel = null;
        cachedX = Integer.MIN_VALUE;
        cachedY = Integer.MIN_VALUE;
        cachedZ = Integer.MIN_VALUE;
        cachedWorldRevision = Long.MIN_VALUE;
        cachedArea = null;
        cachedBlocks = Map.of();
        cachedIndex = BlockIndex.EMPTY;
        cachedCollisions = List.of();
    }

    private void rebuildWorldSnapshot(Player player, ClientWorldTracker clientWorld, String levelName,
                                      int centerX, int centerY, int centerZ, long worldRevision) {
        Aabb area = new Aabb(centerX - BLOCK_RADIUS, centerY - 2, centerZ - BLOCK_RADIUS,
                centerX + BLOCK_RADIUS + 1, centerY + 4, centerZ + BLOCK_RADIUS + 1);
        boolean walksOnPowderSnow = walksOnPowderSnow(player);
        Map<BlockPos, BlockFrame> blocks = new HashMap<>();
        for (int x = centerX - BLOCK_RADIUS; x <= centerX + BLOCK_RADIUS; x++) {
            for (int z = centerZ - BLOCK_RADIUS; z <= centerZ + BLOCK_RADIUS; z++) {
                for (int y = centerY - 2; y <= centerY + 3; y++) {
                    Block primary = player.getLevel().getBlock(x, y, z, 0);
                    Block extra = player.getLevel().getBlock(x, y, z, 1);
                    BlockFrame primaryFrame = clientWorld.resolve(x, y, z, 0, captureBlock(primary, walksOnPowderSnow));
                    BlockFrame extraFrame = clientWorld.resolve(x, y, z, 1, captureBlock(extra, walksOnPowderSnow));
                    BlockFrame combined = BlockFrame.combine(primaryFrame, extraFrame);
                    if (combined == null || !combined.relevant()) continue;
                    blocks.put(new BlockPos(x, y, z), combined);
                }
            }
        }
        reshapeStairs(blocks);
        cachedLevel = levelName;
        cachedX = centerX;
        cachedY = centerY;
        cachedZ = centerZ;
        cachedWorldRevision = worldRevision;
        cachedArea = area;
        cachedBlocks = Collections.unmodifiableMap(blocks);
        cachedIndex = BlockIndex.of(cachedBlocks);
        cachedCollisions = orderedCollisions(blocks);
    }

    /** Recomputes stair collision boxes once the whole volume is captured. */
    private static void reshapeStairs(Map<BlockPos, BlockFrame> blocks) {
        StairShapes.Neighbours neighbours = (x, y, z) -> blocks.get(new BlockPos(x, y, z));
        for (Map.Entry<BlockPos, BlockFrame> entry : blocks.entrySet()) {
            BlockFrame frame = entry.getValue();
            if (frame.stair() == null) continue;
            BlockPos position = entry.getKey();
            entry.setValue(frame.withCollisions(StairShapes.collisions(position.x(), position.y(),
                    position.z(), frame.stair(), neighbours)));
        }
    }

    private void applyWorldChanges(Player player, ClientWorldTracker clientWorld,
                                   Map<ClientWorldTracker.Key, ClientWorldTracker.Update> changes,
                                   long worldRevision) {
        if (changes.isEmpty()) {
            cachedWorldRevision = worldRevision;
            return;
        }
        boolean walksOnPowderSnow = walksOnPowderSnow(player);
        Map<BlockPos, BlockFrame> updated = null;
        for (ClientWorldTracker.Key key : changes.keySet()) {
            if (key.x() < cachedArea.minX() || key.x() >= cachedArea.maxX()
                    || key.y() < cachedArea.minY() || key.y() >= cachedArea.maxY()
                    || key.z() < cachedArea.minZ() || key.z() >= cachedArea.maxZ()) continue;
            if (updated == null) updated = new HashMap<>(cachedBlocks);
            Block primary = player.getLevel().getBlock(key.x(), key.y(), key.z(), 0);
            Block extra = player.getLevel().getBlock(key.x(), key.y(), key.z(), 1);
            BlockFrame primaryFrame = clientWorld.resolve(key.x(), key.y(), key.z(), 0, captureBlock(primary, walksOnPowderSnow));
            BlockFrame extraFrame = clientWorld.resolve(key.x(), key.y(), key.z(), 1, captureBlock(extra, walksOnPowderSnow));
            BlockFrame combined = BlockFrame.combine(primaryFrame, extraFrame);
            BlockPos position = new BlockPos(key.x(), key.y(), key.z());
            if (combined == null || !combined.relevant()) updated.remove(position);
            else updated.put(position, combined);
        }
        if (updated != null) {
            reshapeStairs(updated);
            cachedBlocks = Collections.unmodifiableMap(updated);
            cachedIndex = BlockIndex.of(cachedBlocks);
            cachedCollisions = orderedCollisions(updated);
        }
        cachedWorldRevision = worldRevision;
    }

    private static BlockFrame captureBlock(Block block, boolean walksOnPowderSnow) {
        return BlockFrame.capture(block, walksOnPowderSnow);
    }

    private static boolean walksOnPowderSnow(Player player) {
        return ItemID.LEATHER_BOOTS.equals(player.getInventory().getBoots().getId());
    }

    private static List<Aabb> orderedCollisions(Map<BlockPos, BlockFrame> blocks) {
        List<Map.Entry<BlockPos, BlockFrame>> entries = new ArrayList<>(blocks.entrySet());
        entries.sort((first, second) -> {
            int x = Integer.compare(first.getKey().x(), second.getKey().x());
            if (x != 0) return x;
            int z = Integer.compare(first.getKey().z(), second.getKey().z());
            if (z != 0) return z;
            return Integer.compare(first.getKey().y(), second.getKey().y());
        });
        List<Aabb> collisions = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockFrame> entry : entries) {
            collisions.addAll(entry.getValue().collisions());
        }
        return List.copyOf(collisions);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int amplifier(Player player, EffectType type) {
        Effect effect = player.getEffect(type);
        return effect == null ? -1 : effect.getAmplifier();
    }

    private static double attribute(Player player, int id, double fallback) {
        Attribute attribute = player.getAttributes().get(id);
        return attribute == null ? fallback : attribute.getValue();
    }

    private static double knockbackResistance(Player player) {
        double armor = 0;
        for (Item item : player.getInventory().getArmorInventory().getContents().values()) {
            if (item != null && !item.isNull()) armor += item.getKnockbackResistance();
        }
        return 1 - (1 - armor) * (1 - player.getKnockbackResistance());
    }
}
