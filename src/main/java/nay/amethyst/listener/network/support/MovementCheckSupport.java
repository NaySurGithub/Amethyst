package nay.amethyst.listener.network.support;

import nay.amethyst.data.player.PlayerData;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.history.model.Aabb;
import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.history.model.BlockPos;
import nay.amethyst.history.model.WorldFrame;
import nay.amethyst.prediction.common.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.powernukkitx.Player;
import org.powernukkitx.block.BlockFenceGate;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.math.SimpleAxisAlignedBB;
import org.powernukkitx.level.Location;

import java.util.Set;

public final class MovementCheckSupport {
    private static final double HITBOX_INSET = 0.03;
    private static final double CUBE_EPSILON = 1.0E-6;
    private static final double MOVING_BLOCK_RANGE = 1.0;
    private static final Set<String> SHAPE_EXEMPT_BLOCKS = Set.of(BlockID.MOVING_BLOCK,
            BlockID.PISTON, BlockID.STICKY_PISTON, BlockID.PISTON_ARM_COLLISION,
            BlockID.STICKY_PISTON_ARM_COLLISION, BlockID.BAMBOO, BlockID.SCAFFOLDING);

    private MovementCheckSupport() {
    }

    public static boolean isMovementCheck(CheckType check) {
        return check == CheckType.SIMULATION || check == CheckType.FLY_A || check == CheckType.TIMER
                || check == CheckType.VEHICLE_A || check == CheckType.PHASE_A
                || check == CheckType.NO_FALL_A || check == CheckType.VELOCITY_A;
    }

    public static boolean collides(Player player, Vector3f position) {
        double half = player.getWidth() / 2.0 - 0.03;
        double feet = position.getY() - player.getBaseOffset();
        AxisAlignedBB box = new SimpleAxisAlignedBB(position.getX() - half, feet + 0.03, position.getZ() - half,
                position.getX() + half, feet + player.getHeight() - 0.03, position.getZ() + half);
        for (Block block : player.getLevel().getCollisionBlocks(box, true)) {
            if (block instanceof BlockFenceGate gate && gate.isOpen()) continue;
            return true;
        }
        return false;
    }

    /**
     * Whether the player's hitbox overlaps a block that is a full solid cube. Blocks carrying any
     * custom collision shape are ignored, so only a wall the client cannot legally stand inside is
     * reported.
     */
    public static boolean insideFullCube(WorldFrame frame, Vector3f position) {
        double half = frame.physics().width() / 2.0 - HITBOX_INSET;
        double feet = position.getY() - frame.physics().baseOffset();
        double minimumX = position.getX() - half;
        double maximumX = position.getX() + half;
        double minimumY = feet + HITBOX_INSET;
        double maximumY = feet + frame.physics().height() - HITBOX_INSET;
        double minimumZ = position.getZ() - half;
        double maximumZ = position.getZ() + half;
        if (maximumY <= minimumY) {
            return false;
        }

        for (int x = floor(minimumX); x <= floor(maximumX); x++) {
            for (int y = floor(minimumY); y <= floor(maximumY); y++) {
                for (int z = floor(minimumZ); z <= floor(maximumZ); z++) {
                    if (!frame.coversBlock(x, y, z)) {
                        continue;
                    }
                    if (!isFullCube(frame.blockAt(x, y, z), x, y, z)) {
                        continue;
                    }
                    if (x + 1.0 > minimumX && x < maximumX
                            && y + 1.0 > minimumY && y < maximumY
                            && z + 1.0 > minimumZ && z < maximumZ) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether a piston is moving a block within a block of the player. */
    public static boolean nearMovingBlock(WorldFrame frame, Vector3f position) {
        if (frame.index().moving().isEmpty()) {
            return false;
        }

        double half = frame.physics().width() / 2.0 + MOVING_BLOCK_RANGE;
        double feet = position.getY() - frame.physics().baseOffset();
        double minimumX = position.getX() - half;
        double maximumX = position.getX() + half;
        double minimumY = feet - MOVING_BLOCK_RANGE;
        double maximumY = feet + frame.physics().height() + MOVING_BLOCK_RANGE;
        double minimumZ = position.getZ() - half;
        double maximumZ = position.getZ() + half;
        for (BlockPos moving : frame.index().moving()) {
            if (moving.x() + 1.0 > minimumX && moving.x() < maximumX
                    && moving.y() + 1.0 > minimumY && moving.y() < maximumY
                    && moving.z() + 1.0 > minimumZ && moving.z() < maximumZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFullCube(BlockFrame block, int x, int y, int z) {
        if (block == null || block.water() || block.lava() || block.climbable()
                || block.stair() != null || block.collisions().size() != 1) {
            return false;
        }
        if (SHAPE_EXEMPT_BLOCKS.contains(block.id())) {
            return false;
        }
        Aabb box = block.collisions().get(0);
        return Math.abs(box.minX() - x) <= CUBE_EPSILON
                && Math.abs(box.minY() - y) <= CUBE_EPSILON
                && Math.abs(box.minZ() - z) <= CUBE_EPSILON
                && Math.abs(box.maxX() - (x + 1.0)) <= CUBE_EPSILON
                && Math.abs(box.maxY() - (y + 1.0)) <= CUBE_EPSILON
                && Math.abs(box.maxZ() - (z + 1.0)) <= CUBE_EPSILON;
    }

    /** Whether anything under the player has a collision box smaller than a full cube. */
    public static boolean nearPartialBlock(Player player, Vector3f position) {
        double half = player.getWidth() / 2.0;
        double feet = position.getY() - player.getBaseOffset();
        int minimumX = floor((float) (position.getX() - half));
        int maximumX = floor((float) (position.getX() + half));
        int minimumY = floor((float) (feet - 0.35));
        int maximumY = floor((float) (feet + 0.02));
        int minimumZ = floor((float) (position.getZ() - half));
        int maximumZ = floor((float) (position.getZ() + half));
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    Block block = player.getLevel().getBlock(x, y, z, 0);
                    if (block == null || block.isAir()) continue;
                    AxisAlignedBB box = block.getBoundingBox();
                    if (box == null) continue;
                    if (box.getMaxY() - box.getMinY() < 1.0 - 1.0E-6
                            || box.getMaxX() - box.getMinX() < 1.0 - 1.0E-6
                            || box.getMaxZ() - box.getMinZ() < 1.0 - 1.0E-6) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean insideCobweb(Player player, Vector3f position) {
        double half = player.getWidth() / 2.0;
        double feet = position.getY() - player.getBaseOffset();
        int minimumX = floor((float) (position.getX() - half));
        int maximumX = floor((float) (position.getX() + half));
        int minimumY = floor((float) feet);
        int maximumY = floor((float) (feet + player.getHeight()));
        int minimumZ = floor((float) (position.getZ() - half));
        int maximumZ = floor((float) (position.getZ() + half));
        for (int x = minimumX; x <= maximumX; x++) {
            for (int y = minimumY; y <= maximumY; y++) {
                for (int z = minimumZ; z <= maximumZ; z++) {
                    if (BlockID.WEB.equals(player.getLevel().getBlock(x, y, z, 0).getId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean serverGround(Player player, Vector3f position) {
        double half = player.getWidth() / 2.0 - (player.isSneaking() ? 0.005 : 0.04);
        double feet = position.getY() - player.getBaseOffset();
        AxisAlignedBB box = new SimpleAxisAlignedBB(position.getX() - half, feet - 0.35, position.getZ() - half,
                position.getX() + half, feet + 0.02, position.getZ() + half);
        return player.getLevel().getCollisionBlocks(box, true).length > 0;
    }

    public static Location clientLocation(Player player, Vector3f position, Vector3f rotation) {
        return new Location(position.getX(), position.getY() - player.getBaseOffset(), position.getZ(),
                rotation.getY(), rotation.getX(), player.getLevel());
    }

    public static boolean finite(Vector3f vector) {
        return vector != null && Float.isFinite(vector.getX()) && Float.isFinite(vector.getY()) && Float.isFinite(vector.getZ());
    }

    public static double squared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
