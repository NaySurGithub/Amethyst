package nay.amethyst.check.scaffold;

import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseInventoryTransaction;
import org.powernukkitx.Player;
import org.powernukkitx.item.Item;

/**
 * Refuses a placement made against a block the player is not looking at. The allowance is deliberately
 * wide, so only a placement clearly beside or behind the player is refused.
 */
public final class WeirdPlaceCheck {

    private static final double MAXIMUM_ANGLE = 80.0;

    private WeirdPlaceCheck() {
    }

    public static Result inspect(Player player, PlayerData data,
                                 ItemUseInventoryTransaction transaction) {
        Vector3i block = transaction.getPosition();
        Vector3f click = transaction.getClickPosition();
        if (block == null || click == null || data.inGrace()) {
            return null;
        }

        Result held = inspectHeldItem(player, transaction);
        if (held != null) {
            return held;
        }

        double targetX = block.getX() + click.getX();
        double targetY = block.getY() + click.getY();
        double targetZ = block.getZ() + click.getZ();

        double toX = targetX - player.getX();
        double toY = targetY - (player.getY() + player.getEyeHeight());
        double toZ = targetZ - player.getZ();
        double distance = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (distance < 1.0E-4) {
            return null;
        }

        double yaw = Math.toRadians(player.yaw);
        double pitch = Math.toRadians(player.pitch);
        double cosPitch = Math.cos(pitch);
        double lookX = -Math.sin(yaw) * cosPitch;
        double lookY = -Math.sin(pitch);
        double lookZ = Math.cos(yaw) * cosPitch;

        double alignment = (lookX * toX + lookY * toY + lookZ * toZ) / distance;
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, alignment))));
        if (angle <= MAXIMUM_ANGLE) {
            return null;
        }

        return new Result("angle=" + Math.round(angle) + " at " + block);
    }

    /** The item the placement claims has to be the one actually in the player's hand. */
    private static Result inspectHeldItem(Player player, ItemUseInventoryTransaction transaction) {
        ItemData sent = transaction.getItem();
        if (sent == null || sent.getDefinition() == null) {
            return null;
        }

        String claimed = sent.getDefinition().getIdentifier();
        if (claimed == null || claimed.isEmpty() || "minecraft:air".equals(claimed)) {
            return null;
        }

        Item inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.isNull()) {
            return new Result("placed " + claimed + " with an empty hand");
        }
        if (!claimed.equals(inHand.getId())) {
            return new Result("placed " + claimed + " while holding " + inHand.getId());
        }
        return null;
    }

    public record Result(String detail) {
    }
}
