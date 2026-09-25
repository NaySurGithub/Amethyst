package nay.amethyst.player;

import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;

import java.util.List;

public final class ClientPlayerState {
    private boolean ready;
    private boolean wearingElytra;
    private boolean wearingLeatherBoots;
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

    public synchronized boolean wearingLeatherBoots() {
        return wearingLeatherBoots;
    }

    public synchronized void applyArmorContent(List<ItemData> armor) {
        wearingElytra = armor.size() > 1 && is(armor.get(1), "minecraft:elytra");
        wearingLeatherBoots = armor.size() > 3 && is(armor.get(3), "minecraft:leather_boots");
    }

    public synchronized void applyArmorSlot(int slot, ItemData item) {
        if (slot == 1) {
            wearingElytra = is(item, "minecraft:elytra");
        } else if (slot == 3) {
            wearingLeatherBoots = is(item, "minecraft:leather_boots");
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

    private static boolean is(ItemData item, String identifier) {
        return item != null && !item.isNull() && item.getDefinition() != null
                && identifier.equals(item.getDefinition().getIdentifier());
    }
}
