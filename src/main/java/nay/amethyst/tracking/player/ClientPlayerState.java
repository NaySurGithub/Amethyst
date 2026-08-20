package nay.amethyst.tracking.player;

import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.List;

public final class ClientPlayerState {
    private boolean ready;
    private boolean wearingElytra;
    private GameType gameType = GameType.SURVIVAL;

    public synchronized boolean ready() {
        return ready;
    }

    public synchronized void ready(boolean ready) {
        this.ready = ready;
    }

    public synchronized boolean wearingElytra() {
        return wearingElytra;
    }

    public synchronized void applyArmorContent(List<ItemData> armor) {
        wearingElytra = armor.size() > 1 && isElytra(armor.get(1));
    }

    public synchronized void applyArmorSlot(int slot, ItemData item) {
        if (slot == 1) {
            wearingElytra = isElytra(item);
        }
    }

    public synchronized GameType gameType() {
        return gameType;
    }

    public synchronized void gameType(GameType gameType) {
        if (gameType != null && gameType != GameType.DEFAULT) {
            this.gameType = gameType;
        }
    }

    private static boolean isElytra(ItemData item) {
        return item != null && !item.isNull() && item.getDefinition() != null
                && "minecraft:elytra".equals(item.getDefinition().getIdentifier());
    }
}
