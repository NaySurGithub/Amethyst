package nay.amethyst.protect;

import nay.amethyst.config.AmethystSettings;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockEntityHolder;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.block.BlockState;
import org.powernukkitx.blockentity.BlockEntity;
import org.powernukkitx.blockentity.BlockEntityInventoryHolder;
import org.powernukkitx.level.Level;
import org.powernukkitx.level.format.IChunk;
import org.powernukkitx.math.BlockFace;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Replaces containers the player has no line of sight to with a plain block, so a client that draws
 * through terrain has nothing to draw. A container comes back as soon as the player is close to
 * seeing it, judged from eye positions pushed aside by a margin so the swap lands before the sight
 * line opens rather than after.
 */
public final class ContainerConcealer {
    private static final BlockFace[] FACES = BlockFace.values();
    private static final double SURFACE_INSET = 0.02;
    private static final int COORDINATE_BITS = 26;
    private static final int HEIGHT_OFFSET = 2048;
    private static final int MAX_IDLE_PASSES = 5;
    private static final double STILL_EYE_SQUARED = 1.0E-4;
    private static final int MAX_TRACKED_CHANGES = 65_536;

    /**
     * The five points aimed at on each face, as offsets inside the block: its middle and its four
     * corners, pulled off the surface so a ray does not graze the neighbor. Held flat, three
     * numbers per point, to keep the sight test free of allocation.
     */
    private static final double[][] FACE_SAMPLES = buildFaceSamples();

    private static final long[] EMPTY = new long[0];

    /**
     * Whether a block state stops sight, worked out once per state. Glass, water and ice let light
     * through and so never hide anything; a block that dampens light fully but does not fill its
     * cell does not either, which is what a custom shape can be.
     */
    private static final Map<BlockState, Boolean> OCCLUSION = new ConcurrentHashMap<>();

    private final Map<UUID, PlayerView> views = new ConcurrentHashMap<>();
    private final Map<ChunkKey, long[]> chunkIndex = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Long> chunkChanges = new ConcurrentHashMap<>();
    private final AtomicLong changeCounter = new AtomicLong();
    private volatile long changesResetAt;
    private volatile AmethystSettings indexedWith;

    /**
     * Reconsiders every container around {@code player}. Cheap on the common case: a container
     * walled in on all six sides is answered from its neighbors alone, without casting a ray.
     */
    public void refresh(Player player, AmethystSettings settings) {
        if (player == null || !player.isOnline()) {
            return;
        }
        Level level = player.getLevel();
        if (level == null) {
            return;
        }
        if (settings != indexedWith) {
            chunkIndex.clear();
            indexedWith = settings;
        }
        PlayerView view = views.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerView());
        synchronized (view) {
            if (view.closed) {
                return;
            }
            if (view.levelId != level.getId()) {
                view.concealed.clear();
                view.levelId = level.getId();
                view.dirty = true;
            }
            double eyeX = player.getX();
            double eyeY = player.getY() + player.getEyeHeight();
            double eyeZ = player.getZ();
            if (unchanged(view, level, player, settings, eyeX, eyeY, eyeZ)) {
                view.idlePasses++;
                return;
            }
            long stamp = changeCounter.get();
            scan(player, level, view, settings);
            view.eyeX = eyeX;
            view.eyeY = eyeY;
            view.eyeZ = eyeZ;
            view.scannedAt = stamp;
            view.scannedWith = settings;
            view.idlePasses = 0;
            view.dirty = false;
        }
    }

    private boolean unchanged(PlayerView view, Level level, Player player, AmethystSettings settings,
                              double eyeX, double eyeY, double eyeZ) {
        if (view.dirty || view.scannedWith != settings || view.idlePasses >= MAX_IDLE_PASSES
                || view.scannedAt < changesResetAt) {
            return false;
        }
        double dx = eyeX - view.eyeX;
        double dy = eyeY - view.eyeY;
        double dz = eyeZ - view.eyeZ;
        if (dx * dx + dy * dy + dz * dz > STILL_EYE_SQUARED) {
            return false;
        }
        int radius = settings.concealRadius();
        int minChunkX = (player.getFloorX() - radius) >> 4;
        int maxChunkX = (player.getFloorX() + radius) >> 4;
        int minChunkZ = (player.getFloorZ() - radius) >> 4;
        int maxChunkZ = (player.getFloorZ() + radius) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                Long changedAt = chunkChanges.get(new ChunkKey(level.getId(), chunkX, chunkZ));
                if (changedAt != null && changedAt > view.scannedAt) {
                    return false;
                }
            }
        }
        return true;
    }

    private void scan(Player player, Level level, PlayerView view, AmethystSettings settings) {
        double eyeX = player.getX();
        double eyeY = player.getY() + player.getEyeHeight();
        double eyeZ = player.getZ();
        double margin = settings.concealMargin();

        double near = settings.concealMinDistance();
        double nearSquared = near * near;

        LongSet seen = new LongOpenHashSet();
        for (long key : collectContainers(level, player, settings)) {
            int x = unpackX(key);
            int y = unpackY(key);
            int z = unpackZ(key);
            seen.add(key);
            if (withinRange(player, x, y, z, nearSquared)) {
                if (view.concealed.remove(key)) {
                    sendReal(player, level, x, y, z);
                }
                continue;
            }
            if (isHidden(level, eyeX, eyeY, eyeZ, x, y, z, margin)) {
                if (view.concealed.add(key)) {
                    sendCover(player, level, x, y, z);
                }
            } else if (view.concealed.remove(key)) {
                sendReal(player, level, x, y, z);
            }
        }


        view.concealed.removeIf(key -> {
            if (seen.contains(key)) {
                return false;
            }
            sendReal(player, level, unpackX(key), unpackY(key), unpackZ(key));
            return true;
        });
    }

    /**
     * @return whether a block update for this position is one this class sent, and so must not be
     * taken for something the client was really told about the world
     */
    public boolean conceals(Player player, Vector3i position) {
        if (player == null || position == null) {
            return false;
        }
        PlayerView view = views.get(player.getUniqueId());
        if (view == null || view.levelId != player.getLevel().getId()) {
            return false;
        }
        return view.concealed.contains(key(position.getX(), position.getY(), position.getZ()));
    }

    /**
     * Drops what is remembered about a chunk the player is being sent again. The chunk carries the
     * real containers, so the covers have to be sent a second time rather than assumed still in
     * place.
     */
    public void forgetChunk(Player player, int chunkX, int chunkZ) {
        PlayerView view = player == null ? null : views.get(player.getUniqueId());
        if (view == null) {
            return;
        }
        view.concealed.removeIf(key -> (unpackX(key) >> 4) == chunkX && (unpackZ(key) >> 4) == chunkZ);
        view.dirty = true;
    }

    /**
     * Puts back every container hidden from a player, and forgets them. Called when they leave, and
     * when the feature is turned off.
     */
    public void reveal(Player player) {
        PlayerView view = player == null ? null : views.remove(player.getUniqueId());
        if (view == null) {
            return;
        }
        synchronized (view) {
            view.closed = true;
            Level level = player.isOnline() ? player.getLevel() : null;
            if (level != null && view.levelId == level.getId()) {
                for (long key : view.concealed) {
                    sendReal(player, level, unpackX(key), unpackY(key), unpackZ(key));
                }
            }
            view.concealed.clear();
        }
    }

    public void clear() {
        views.clear();
        chunkIndex.clear();
        chunkChanges.clear();
    }

    private static boolean withinRange(Player player, int x, int y, int z, double nearSquared) {
        double dx = x + 0.5 - player.getX();
        double dy = y + 0.5 - player.getY();
        double dz = z + 0.5 - player.getZ();
        return dx * dx + dy * dy + dz * dz <= nearSquared;
    }

    private LongSet collectContainers(Level level, Player player, AmethystSettings settings) {
        int radius = settings.concealRadius();
        LongSet containers = new LongOpenHashSet();
        double playerX = player.getX();
        double playerY = player.getY();
        double playerZ = player.getZ();
        int minChunkX = (player.getFloorX() - radius) >> 4;
        int maxChunkX = (player.getFloorX() + radius) >> 4;
        int minChunkZ = (player.getFloorZ() - radius) >> 4;
        int maxChunkZ = (player.getFloorZ() + radius) >> 4;
        int radiusSquared = radius * radius;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                for (long key : chunkContainers(level, chunkX, chunkZ, settings)) {
                    double dx = unpackX(key) + 0.5 - playerX;
                    double dy = unpackY(key) + 0.5 - playerY;
                    double dz = unpackZ(key) + 0.5 - playerZ;
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                        containers.add(key);
                    }
                }
            }
        }
        return containers;
    }

    /**
     * The containers of one chunk, remembered until something is built or broken there. Block
     * entities move rarely, and walking the whole neighborhood on every pass was most of the work.
     */
    private long[] chunkContainers(Level level, int chunkX, int chunkZ, AmethystSettings settings) {
        ChunkKey chunkKey = new ChunkKey(level.getId(), chunkX, chunkZ);
        long[] cached = chunkIndex.get(chunkKey);
        if (cached != null) {
            return cached;
        }
        IChunk chunk = level.getChunkIfLoaded(chunkX, chunkZ);
        if (chunk == null) {
            return EMPTY;
        }
        LongSet found = new LongOpenHashSet();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntityInventoryHolder && !blockEntity.closed
                    && !settings.disabledContainers(blockEntity.getBlock().getId())) {
                found.add(key(blockEntity.getFloorX(), blockEntity.getFloorY(), blockEntity.getFloorZ()));
            }
        }
        long[] positions = found.toLongArray();
        chunkIndex.put(chunkKey, positions);
        return positions;
    }

    /**
     * Forgets the containers remembered for the chunk holding this position. Called whenever a block
     * is placed or broken, since either may add or remove a container.
     */
    public void invalidate(Level level, int x, int z) {
        if (level != null) {
            ChunkKey chunkKey = new ChunkKey(level.getId(), x >> 4, z >> 4);
            chunkIndex.remove(chunkKey);
            long stamp = changeCounter.incrementAndGet();
            if (chunkChanges.size() >= MAX_TRACKED_CHANGES) {
                chunkChanges.clear();
                changesResetAt = stamp;
            }
            chunkChanges.put(chunkKey, stamp);
        }
    }

    /**
     * A container is hidden when no ray from the eye reaches one of its exposed faces, nor from the
     * two eyes set aside from it. Those two are placed level with the player and square to the
     * container, the only direction a step actually opens a sight line from.
     */
    private boolean isHidden(Level level, double eyeX, double eyeY, double eyeZ,
                             int x, int y, int z, double margin) {
        List<BlockFace> exposed = exposedFaces(level, x, y, z);
        if (exposed.isEmpty()) {
            return true;
        }
        if (anyFaceVisible(level, eyeX, eyeY, eyeZ, x, y, z, exposed)) {
            return false;
        }
        if (margin <= 0) {
            return true;
        }

        double dx = x + 0.5 - eyeX;
        double dz = z + 0.5 - eyeZ;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double sideX;
        double sideZ;
        if (flat < 1.0E-4) {
            sideX = margin;
            sideZ = 0;
        } else {
            sideX = -dz / flat * margin;
            sideZ = dx / flat * margin;
        }
        return !anyFaceVisible(level, eyeX + sideX, eyeY, eyeZ + sideZ, x, y, z, exposed)
                && !anyFaceVisible(level, eyeX - sideX, eyeY, eyeZ - sideZ, x, y, z, exposed);
    }

    private List<BlockFace> exposedFaces(Level level, int x, int y, int z) {
        List<BlockFace> exposed = new ArrayList<>(6);
        for (BlockFace face : FACES) {
            if (!occludes(level, x + face.getXOffset(), y + face.getYOffset(), z + face.getZOffset())) {
                exposed.add(face);
            }
        }
        return exposed;
    }

    private boolean anyFaceVisible(Level level, double eyeX, double eyeY, double eyeZ,
                                   int x, int y, int z, List<BlockFace> exposed) {
        for (BlockFace face : exposed) {
            if (face.getXOffset() * (eyeX - (x + 0.5))
                    + face.getYOffset() * (eyeY - (y + 0.5))
                    + face.getZOffset() * (eyeZ - (z + 0.5)) <= 0) {
                continue;
            }
            double[] samples = FACE_SAMPLES[face.getIndex()];
            for (int i = 0; i < samples.length; i += 3) {
                if (clearLine(level, eyeX, eyeY, eyeZ,
                        x + samples[i], y + samples[i + 1], z + samples[i + 2], x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double[][] buildFaceSamples() {
        double low = SURFACE_INSET;
        double high = 1 - SURFACE_INSET;
        double[][] samples = new double[FACES.length][];
        for (BlockFace face : FACES) {
            double minX = face == BlockFace.EAST ? high : low;
            double maxX = face == BlockFace.WEST ? low : high;
            double minY = face == BlockFace.UP ? high : low;
            double maxY = face == BlockFace.DOWN ? low : high;
            double minZ = face == BlockFace.SOUTH ? high : low;
            double maxZ = face == BlockFace.NORTH ? low : high;
            samples[face.getIndex()] = new double[]{
                    (minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2,
                    minX, minY, minZ,
                    maxX, minY, maxZ,
                    minX, maxY, maxZ,
                    maxX, maxY, minZ
            };
        }
        return samples;
    }

    /**
     * Walks the blocks the segment crosses, in order, and reports whether it reaches the target
     * without meeting anything the player cannot see through. The container itself is skipped.
     */
    private boolean clearLine(Level level, double fromX, double fromY, double fromZ,
                              double toX, double toY, double toZ, int skipX, int skipY, int skipZ) {
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) {
            return true;
        }
        dx /= length;
        dy /= length;
        dz /= length;

        int x = (int) Math.floor(fromX);
        int y = (int) Math.floor(fromY);
        int z = (int) Math.floor(fromZ);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double deltaX = Math.abs(1 / dx);
        double deltaY = Math.abs(1 / dy);
        double deltaZ = Math.abs(1 / dz);
        double nextX = boundary(fromX, x, stepX, deltaX);
        double nextY = boundary(fromY, y, stepY, deltaY);
        double nextZ = boundary(fromZ, z, stepZ, deltaZ);

        int targetX = (int) Math.floor(toX);
        int targetY = (int) Math.floor(toY);
        int targetZ = (int) Math.floor(toZ);
        if (!(x == skipX && y == skipY && z == skipZ) && occludes(level, x, y, z)) {
            return false;
        }
        while (true) {
            if (x == targetX && y == targetY && z == targetZ) {
                return true;
            }
            // On an exact tie the segment runs along an edge or a corner. Stepping one axis would
            // let it slip between two blocks that touch there, so the block it grazes counts too.
            if (nextX <= nextY && nextX <= nextZ) {
                if (nextX > length) return false;
                if (nextX == nextY && occludes(level, x, y + stepY, z)) return false;
                if (nextX == nextZ && occludes(level, x, y, z + stepZ)) return false;
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                if (nextY > length) return false;
                if (nextY == nextZ && occludes(level, x, y, z + stepZ)) return false;
                y += stepY;
                nextY += deltaY;
            } else {
                if (nextZ > length) return false;
                z += stepZ;
                nextZ += deltaZ;
            }
            if (x == skipX && y == skipY && z == skipZ) {
                continue;
            }
            if (occludes(level, x, y, z)) {
                return false;
            }
        }
    }

    private static double boundary(double from, int block, int step, double delta) {
        if (Double.isInfinite(delta)) {
            return Double.POSITIVE_INFINITY;
        }
        double edge = step > 0 ? block + 1 - from : from - block;
        return edge * delta;
    }

    /**
     * Reads through the per-tick block cache, the same one the engine's own block walkers use, so
     * the rays cast at one container do not pay for the ground they share.
     */
    private static boolean occludes(Level level, int x, int y, int z) {
        Block block = level.getTickCachedBlock(x, y, z, 0, false);
        if (block == null) {
            return false;
        }
        Boolean known = OCCLUSION.get(block.getBlockState());
        if (known != null) {
            return known;
        }
        boolean occludes = block.getLightFilter() >= 15 && block.isFullBlock();
        OCCLUSION.put(block.getBlockState(), occludes);
        return occludes;
    }

    private void sendCover(Player player, Level level, int x, int y, int z) {
        level.sendBlocks(new Player[]{player}, new Block[]{cover(level, x, y, z)},
                UpdateBlockPacket.FLAG_ALL, 0);
    }

    private void sendReal(Player player, Level level, int x, int y, int z) {
        if (level.getChunkIfLoaded(x >> 4, z >> 4) == null) {
            return;
        }
        level.sendBlocks(new Player[]{player}, new Block[]{level.getBlock(x, y, z, false)},
                UpdateBlockPacket.FLAG_ALL, 0);
    }

    /**
     * Picks what the container is replaced with: the state of whichever block already surrounds it,
     * so the swap reads as more of the same wall rather than a block of stone in a wall of
     * deepslate. A container walled in by nothing is replaced by air instead, because a lone block
     * standing in the open would point at it more plainly than the container itself.
     */
    private Block cover(Level level, int x, int y, int z) {
        Map<Block, Integer> counts = new HashMap<>();
        for (BlockFace face : FACES) {
            Block neighbour = level.getTickCachedBlock(x + face.getXOffset(), y + face.getYOffset(),
                    z + face.getZOffset(), 0, false);
            if (neighbour != null && !(neighbour instanceof BlockEntityHolder<?>)
                    && occludes(level, x + face.getXOffset(), y + face.getYOffset(),
                    z + face.getZOffset())) {
                counts.merge(neighbour, 1, Integer::sum);
            }
        }
        Block best = null;
        int bestCount = 0;
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        if (best == null) {
            return Block.get(BlockID.AIR, level, x, y, z);
        }
        Block cover = best.clone();
        cover.x = x;
        cover.y = y;
        cover.z = z;
        cover.level = level;
        return cover;
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (z & 0x3FFFFFF) << 12)
                | ((y + HEIGHT_OFFSET) & 0xFFF);
    }

    private static int unpackX(long key) {
        return signed((int) ((key >> 38) & 0x3FFFFFF));
    }

    private static int unpackZ(long key) {
        return signed((int) ((key >> 12) & 0x3FFFFFF));
    }

    private static int unpackY(long key) {
        return (int) (key & 0xFFF) - HEIGHT_OFFSET;
    }

    private static int signed(int value) {
        return value >= 1 << (COORDINATE_BITS - 1) ? value - (1 << COORDINATE_BITS) : value;
    }

    private record ChunkKey(int levelId, int x, int z) {
    }

    private static final class PlayerView {
        private final Set<Long> concealed = ConcurrentHashMap.newKeySet();
        private int levelId = Integer.MIN_VALUE;
        private boolean closed;
        private volatile boolean dirty = true;
        private double eyeX;
        private double eyeY;
        private double eyeZ;
        private long scannedAt = -1;
        private int idlePasses;
        private AmethystSettings scannedWith;
    }
}
