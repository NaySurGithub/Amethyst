package nay.amethyst.check.inventory;

import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;

import java.util.List;

public final class ChestStealerCheck {
    private static final long WINDOW_NANOS = 1_000_000_000L;
    private static final int DEFAULT_CPS_LIMIT = 8;
    private static final int BUFFER_THRESHOLD = 3;

    public Result inspect(PlayerData data, ItemStackRequestPacket packet, long now, int cpsLimit) {
        List<ItemStackRequest> requests = packet.getRequests();
        if (requests == null || requests.isEmpty() || data.inGrace()) {
            return Result.CLEAN;
        }

        int containerActions = 0;
        for (ItemStackRequest request : requests) {
            if (request.getActions() == null) continue;
            for (ItemStackRequestAction action : request.getActions()) {
                if (action == null) continue;
                var type = action.getType();
                if (type == ItemStackRequestActionType.TAKE
                        || type == ItemStackRequestActionType.PLACE
                        || type == ItemStackRequestActionType.SWAP) {
                    containerActions++;
                }
            }
        }

        if (containerActions == 0) {
            return Result.CLEAN;
        }

        for (int i = 0; i < containerActions; i++) {
            data.chestClickTimestamps.addLast(now);
        }

        while (!data.chestClickTimestamps.isEmpty()
                && now - data.chestClickTimestamps.peekFirst() > WINDOW_NANOS) {
            data.chestClickTimestamps.pollFirst();
        }

        int limit = cpsLimit > 0 ? cpsLimit : DEFAULT_CPS_LIMIT;
        int clicks = data.chestClickTimestamps.size();
        if (clicks <= limit) {
            data.chestStealerBuffer = Math.max(0, data.chestStealerBuffer - 1);
            return Result.CLEAN;
        }

        data.chestStealerBuffer++;
        if (data.chestStealerBuffer < BUFFER_THRESHOLD) {
            return Result.CLEAN;
        }

        data.chestStealerBuffer = Math.max(0, BUFFER_THRESHOLD - 1);
        return new Result(true, clicks);
    }

    public record Result(boolean failed, int cps) {
        static final Result CLEAN = new Result(false, 0);
    }
}
