package nay.amethyst.check.scaffold;

import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseTriggerType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseInventoryTransaction;
import org.powernukkitx.Player;
import org.powernukkitx.item.Item;

public final class ScaffoldCheck {
    private ScaffoldCheck() {
    }

    public static Result inspect(Player player, PlayerData data, ItemUseInventoryTransaction transaction) {
        if (!data.modernItemUseProtocol || transaction.getClickPosition() == null
                || transaction.getClickPosition().lengthSquared() != 0
                || transaction.getTriggerType() != ItemUseTriggerType.PLAYER_INPUT) {
            return null;
        }
        Item held = player.getInventory().getItemInMainHand();
        if (held != null && isBlockPlaceAlwaysSimulationBased(held.getId())) return null;
        return new Result("zero click vector during player-input placement");
    }

    private static boolean isBlockPlaceAlwaysSimulationBased(String itemId) {
        return "minecraft:vine".equals(itemId) || "minecraft:ladder".equals(itemId);
    }

    public record Result(String detail) {
    }
}
