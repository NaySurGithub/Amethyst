package nay.amethyst.listener.network.support;

import nay.amethyst.data.player.PlayerData;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.prediction.common.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.powernukkitx.Player;
import org.powernukkitx.block.BlockFenceGate;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.math.SimpleAxisAlignedBB;
import org.powernukkitx.level.Location;

public final class MovementCheckSupport {
    private MovementCheckSupport() {
    }

    public static boolean isMovementCheck(CheckType check) {
        return check == CheckType.SIMULATION || check == CheckType.FLY_A || check == CheckType.TIMER
                || check == CheckType.VEHICLE_A
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
}
