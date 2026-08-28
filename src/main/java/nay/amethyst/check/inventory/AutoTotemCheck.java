package nay.amethyst.check.inventory;

import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerEnumName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;

import java.util.List;

public final class AutoTotemCheck {
    private static final long DEFAULT_MIN_SWAP_NANOS = 200_000_000L;
    private static final int BUFFER_THRESHOLD = 2;

    public Result inspect(PlayerData data, ItemStackRequestPacket packet, long now) {
        if (data.lastTotemPopNanos == 0 || data.inGrace()) {
            return Result.CLEAN;
        }

        List<ItemStackRequest> requests = packet.getRequests();
        if (requests == null || requests.isEmpty()) {
            return Result.CLEAN;
        }

        boolean offhandSwap = false;
        for (ItemStackRequest request : requests) {
            if (request.getActions() == null) continue;
            for (ItemStackRequestAction action : request.getActions()) {
                if (action == null) continue;
                if (action.getType() == ItemStackRequestActionType.PLACE && action instanceof PlaceAction place) {
                    if (place.getDestination().getContainerEnumName() == ContainerEnumName.OFFHAND_CONTAINER) {
                        offhandSwap = true;
                        break;
                    }
                } else if (action.getType() == ItemStackRequestActionType.SWAP && action instanceof SwapAction swap) {
                    if (swap.getSource().getContainerEnumName() == ContainerEnumName.OFFHAND_CONTAINER
                            || swap.getDestination().getContainerEnumName() == ContainerEnumName.OFFHAND_CONTAINER) {
                        offhandSwap = true;
                        break;
                    }
                }
            }
            if (offhandSwap) break;
        }

        if (!offhandSwap) {
            return Result.CLEAN;
        }

        long elapsed = now - data.lastTotemPopNanos;
        if (elapsed > DEFAULT_MIN_SWAP_NANOS) {
            data.autoTotemBuffer = Math.max(0, data.autoTotemBuffer - 1);
            return Result.CLEAN;
        }

        data.autoTotemBuffer++;
        if (data.autoTotemBuffer < BUFFER_THRESHOLD) {
            return Result.CLEAN;
        }

        data.autoTotemBuffer = Math.max(0, BUFFER_THRESHOLD - 1);
        long elapsedMs = elapsed / 1_000_000;
        return new Result(true, elapsedMs);
    }

    public record Result(boolean failed, long elapsedMs) {
        static final Result CLEAN = new Result(false, 0);
    }
}
