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
    private static final long FAST_INTERVAL_NANOS = 80_000_000L;
    private static final long SEQUENCE_RESET_NANOS = 1_500_000_000L;
    private static final int FAST_STREAK_THRESHOLD = 3;
    private static final long OPEN_REACTION_NANOS = 250_000_000L;

    public Result inspect(PlayerData data, ItemStackRequestPacket packet, long now, int cpsLimit) {
        List<ItemStackRequest> requests = packet.getRequests();
        if (requests == null || requests.isEmpty() || data.inGrace()) {
            return Result.CLEAN;
        }

        int containerActions = 0;
        int takeActions = 0;
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
                if (type == ItemStackRequestActionType.TAKE) {
                    takeActions++;
                }
            }
        }

        if (containerActions == 0) {
            return Result.CLEAN;
        }

        Result openResult = inspectOpenReaction(data, now, takeActions);
        Result intervalResult = inspectInterval(data, now, containerActions, takeActions);
        if (openResult.failed()) {
            intervalResult = openResult;
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
            return intervalResult;
        }

        data.chestStealerBuffer++;
        if (data.chestStealerBuffer < BUFFER_THRESHOLD) {
            return intervalResult;
        }

        data.chestStealerBuffer = Math.max(0, BUFFER_THRESHOLD - 1);
        return new Result(true, clicks, 0);
    }

    /**
     * Flags the first take that lands before a player could realistically read the container,
     * measured from the moment the server opened it.
     */
    private Result inspectOpenReaction(PlayerData data, long now, int takeActions) {
        if (takeActions == 0 || data.containerOpenedNanos <= 0) {
            return Result.CLEAN;
        }
        long elapsed = now - data.containerOpenedNanos;
        data.containerOpenedNanos = 0;
        if (elapsed < 0 || elapsed > OPEN_REACTION_NANOS) {
            return Result.CLEAN;
        }
        return new Result(true, 0, elapsed / 1_000_000L, true);
    }

    /**
     * Flags a run of container takes that arrive faster than a human can click, which catches
     * containers emptied in a handful of actions where the per-second counter never fills up.
     */
    private Result inspectInterval(PlayerData data, long now, int containerActions, int takeActions) {
        if (takeActions != 1 || containerActions != 1) {
            data.chestFastStreak = 0;
            data.lastChestTakeNanos = takeActions > 0 ? now : 0;
            return Result.CLEAN;
        }

        long previous = data.lastChestTakeNanos;
        data.lastChestTakeNanos = now;
        if (previous <= 0 || now - previous > SEQUENCE_RESET_NANOS) {
            data.chestFastStreak = 0;
            return Result.CLEAN;
        }

        long interval = now - previous;
        if (interval > FAST_INTERVAL_NANOS) {
            data.chestFastStreak = 0;
            return Result.CLEAN;
        }

        data.chestFastStreak++;
        if (data.chestFastStreak < FAST_STREAK_THRESHOLD) {
            return Result.CLEAN;
        }

        data.chestFastStreak = FAST_STREAK_THRESHOLD - 1;
        return new Result(true, 0, interval / 1_000_000L);
    }

    public record Result(boolean failed, int cps, long intervalMs, boolean afterOpen) {
        static final Result CLEAN = new Result(false, 0, 0, false);

        Result(boolean failed, int cps, long intervalMs) {
            this(failed, cps, intervalMs, false);
        }

        public String detail() {
            if (afterOpen) return "open=" + intervalMs + "ms";
            return intervalMs > 0 ? "interval=" + intervalMs + "ms" : "cps=" + cps;
        }
    }
}
