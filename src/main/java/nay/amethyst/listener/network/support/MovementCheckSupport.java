package nay.amethyst.listener.network.support;

import nay.amethyst.data.player.PlayerData;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.prediction.common.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.powernukkitx.Player;
import org.powernukkitx.block.BlockFenceGate;
import org.powernukkitx.block.Block;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.math.SimpleAxisAlignedBB;
import org.powernukkitx.level.Location;
import org.powernukkitx.item.Item;
import org.powernukkitx.item.ItemID;
import org.powernukkitx.item.enchantment.Enchantment;

public final class MovementCheckSupport {
    private MovementCheckSupport() {
    }

    public static boolean isMovementCheck(CheckType check) {
        return check == CheckType.SIMULATION || check == CheckType.VEHICLE_A
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

    public static boolean serverGround(Player player, Vector3f position) {
        double half = player.getWidth() / 2.0 - 0.04;
        double feet = position.getY() - player.getBaseOffset();
        AxisAlignedBB box = new SimpleAxisAlignedBB(position.getX() - half, feet - 0.35, position.getZ() - half,
                position.getX() + half, feet + 0.02, position.getZ() + half);
        return player.getLevel().getCollisionBlocks(box, true).length > 0;
    }

    public static boolean riptideAvailable(Player player) {
        Item item = player.getInventory().getItemInMainHand();
        if (!ItemID.TRIDENT.equals(item.getId())
                || item.getEnchantmentLevel(Enchantment.ID_TRIDENT_RIPTIDE) < 1) {
            return false;
        }
        return player.isTouchingWater()
                || player.getLevel().isRaining() && player.getLevel().canBlockSeeSky(player);
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
