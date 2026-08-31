package nay.amethyst.listener.network.processor;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import nay.amethyst.history.model.BlockFrame;
import nay.amethyst.listener.network.support.MovementCheckSupport;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockID;
import org.powernukkitx.event.block.BlockBreakEvent;
import org.powernukkitx.event.block.BlockPlaceEvent;
import org.powernukkitx.event.server.PacketReceiveEvent;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Map;
import java.util.UUID;

/** Processes block placement, local world updates and block-breaking packets. */
public final class BlockPacketProcessor {
    private final AmethystPlugin plugin;
    private final Map<UUID, PlayerData> players;
    private final ViolationHandler violations;

    /** Creates a block processor backed by the shared player state. */
    public BlockPacketProcessor(AmethystPlugin plugin, Map<UUID, PlayerData> players,
                                ViolationHandler violations) {
        this.plugin = plugin;
        this.players = players;
        this.violations = violations;
    }

    /** Applies a successful server-side placement to the client world model. */
    public void handlePlace(BlockPlaceEvent event) {
        applyLocalBlock(event.getPlayer(), event.getBlock());
    }

    /** Validates reach for a successful block placement. */
    public void inspectPlaceReach(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined || data.inGrace()
                || data.hasMovementCorrection() || data.hasPendingTeleport()) {
            return;
        }

        Block block = event.getBlock();
        double eyeX = data.lastPosition != null ? data.lastPosition.getX() : player.getX();
        double eyeY = data.lastPosition != null ? data.lastPosition.getY()
                : player.getY() + player.getEyeHeight();
        double eyeZ = data.lastPosition != null ? data.lastPosition.getZ() : player.getZ();
        double reach = Math.sqrt(MovementCheckSupport.squared(eyeX, eyeY, eyeZ,
                block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5));
        double maximumReach = Math.min(7.0, Math.max(1.0, plugin.settings().blocksMaxReach()));
        if (reach > maximumReach) {
            violations.fail(event, player, data, CheckType.PLACE_REACH_A, 1,
                    "distance=" + NetworkCheckSupport.format(reach), true, false);
        }
    }

    /** Applies a successful server-side break to the client world model. */
    public void handleBreak(BlockBreakEvent event) {
        Block broken = event.getBlock();
        applyLocalBlock(event.getPlayer(), Block.get(BlockID.AIR, broken.getLevel(),
                broken.getFloorX(), broken.getFloorY(), broken.getFloorZ()));
    }

    private void applyLocalBlock(Player player, Block block) {
        if (player == null || block == null) {
            return;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null || !data.joined) {
            return;
        }
        data.clientWorld.applyLocal(block.getFloorX(), block.getFloorY(), block.getFloorZ(), 0,
                BlockFrame.capture(block));
    }

    /** Validates block actions embedded in an authoritative movement packet. */
    public void inspectActions(PacketReceiveEvent event, Player player, PlayerData data,
                               PlayerAuthInputPacket packet, Vector3f position) {
        if (!packet.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) return;
        var actions = packet.getPlayerBlockActions();
        if (actions == null || actions.isEmpty()) return;
        int maximum = plugin.settings().maxPacketActions();
        if (actions.size() > maximum) {
            violations.fail(event, player, data, CheckType.BAD_PACKET_C, 2,
                    "block-actions=" + actions.size(), true, false);
            return;
        }
        boolean correcting = data.hasMovementCorrection() || data.hasPendingTeleport()
                || data.setbackTeleportPending || data.inGrace();
        long now = System.nanoTime();
        boolean groundedForBreak = breakGrounded(player, data, position);
        for (PlayerBlockActionData action : actions) {
            PlayerActionType type = action.getPlayerActionType();
            if (!isBreakAction(type)) continue;
            Vector3i blockPosition = action.getBlockPosition();
            if (blockPosition == null) continue;
            double reach = Math.sqrt(MovementCheckSupport.squared(
                    position.getX(), position.getY(), position.getZ(),
                    blockPosition.getX() + 0.5, blockPosition.getY() + 0.5,
                    blockPosition.getZ() + 0.5));
            double maximumReach = Math.min(7.0,
                    Math.max(1.0, plugin.settings().blocksMaxReach()));
            if (reach > maximumReach && !correcting) {
                violations.fail(event, player, data, CheckType.BREAK_REACH, 1,
                        "distance=" + NetworkCheckSupport.format(reach), true, false);
                return;
            }

            if (type == PlayerActionType.ABORT_DESTROY_BLOCK
                    || type == PlayerActionType.STOP_DESTROY_BLOCK) {
                data.resetBreak();
                continue;
            }
            if (type == PlayerActionType.START_DESTROY_BLOCK
                    || type == PlayerActionType.CRACK_BLOCK
                    || type == PlayerActionType.CONTINUE_DESTROY_BLOCK) {
                if (!sameBlock(data.breakingBlock, blockPosition)) {
                    startBreaking(player, data, blockPosition, now, groundedForBreak);
                }
                advanceBreakProgress(data, packet.getClientTick());
                continue;
            }
            if (type == PlayerActionType.PREDICT_DESTROY_BLOCK) {
                inspectPredictedBreak(event, player, data, packet.getClientTick(),
                        blockPosition, groundedForBreak, now);
                if (event.isCancelled()) return;
            }
        }
    }

    private void inspectPredictedBreak(PacketReceiveEvent event, Player player, PlayerData data,
                                       long clientTick, Vector3i position, boolean grounded, long now) {
        Block block = clientBreakBlock(player, data, position);
        long required = Math.max(data.requiredBreakNanos,
                requiredBreakNanos(player, block, grounded));
        long leniencyMillis = fastBreakLeniencyMillis(data, clientTick);
        long leniency = leniencyMillis * 1_000_000L;
        advanceBreakProgress(data, clientTick);
        boolean validState = sameBlock(data.breakingBlock, position)
                && block.getId().equals(data.breakingBlockId);
        if (!validState) {
            event.setCancelled();
            data.resetBreak();
            return;
        }
        if (data.breakProgress < 1.0 && now - data.breakStartedNanos + leniency < required) {
            if (data.network.stackLatencyMillis() <= 0) {
                data.resetBreak();
                return;
            }
            event.setCancelled();
            if (registerEarlyBreak(data, now)) {
                violations.fail(event, player, data, CheckType.FAST_BREAK_A, 1,
                        "elapsed=" + Math.max(0, (now - data.breakStartedNanos) / 1_000_000L)
                                + "ms required=" + required / 1_000_000L
                                + "ms leniency=" + leniencyMillis
                                + "ms progress=" + NetworkCheckSupport.format(data.breakProgress)
                                + " rtt=" + data.network.stackLatencyMillis()
                                + "ms block=" + block.getId(), true, false);
            }
            data.resetBreak();
            return;
        }
        data.earlyBreakAttempts = 0;
        data.earlyBreakWindowNanos = 0;
        Block air = Block.get(BlockID.AIR, player.getLevel(),
                position.getX(), position.getY(), position.getZ());
        data.clientWorld.applyLocal(position.getX(), position.getY(), position.getZ(), 0,
                BlockFrame.capture(air));
        data.resetBreak();
    }

    private static boolean isBreakAction(PlayerActionType type) {
        return type == PlayerActionType.START_DESTROY_BLOCK
                || type == PlayerActionType.CRACK_BLOCK
                || type == PlayerActionType.CONTINUE_DESTROY_BLOCK
                || type == PlayerActionType.PREDICT_DESTROY_BLOCK
                || type == PlayerActionType.ABORT_DESTROY_BLOCK
                || type == PlayerActionType.STOP_DESTROY_BLOCK;
    }

    private static boolean sameBlock(Vector3i first, Vector3i second) {
        return first != null && second != null && first.getX() == second.getX()
                && first.getY() == second.getY() && first.getZ() == second.getZ();
    }

    private static void startBreaking(Player player, PlayerData data, Vector3i position,
                                      long now, boolean grounded) {
        Block block = clientBreakBlock(player, data, position);
        data.breakingBlock = position;
        data.breakingBlockId = block.getId();
        data.breakStartedNanos = now;
        data.requiredBreakNanos = requiredBreakNanos(player, block, grounded);
        data.breakProgress = 0;
        data.breakProgressTick = Long.MIN_VALUE;
    }

    private static void advanceBreakProgress(PlayerData data, long clientTick) {
        if (data.breakProgressTick == clientTick || data.requiredBreakNanos <= 0
                || data.requiredBreakNanos == Long.MAX_VALUE) return;
        double requiredTicks = Math.max(1.0, data.requiredBreakNanos / 50_000_000.0);
        data.breakProgress = Math.min(1.0, data.breakProgress + 1.0 / requiredTicks);
        data.breakProgressTick = clientTick;
    }

    private static boolean registerEarlyBreak(PlayerData data, long now) {
        if (data.earlyBreakWindowNanos == 0
                || now - data.earlyBreakWindowNanos > 5_000_000_000L) {
            data.earlyBreakWindowNanos = now;
            data.earlyBreakAttempts = 0;
        }
        data.earlyBreakAttempts++;
        if (data.earlyBreakAttempts < 8) return false;
        data.earlyBreakAttempts = 0;
        data.earlyBreakWindowNanos = now;
        return true;
    }

    private static Block clientBreakBlock(Player player, PlayerData data, Vector3i position) {
        Block server = player.getLevel().getBlock(position.getX(), position.getY(), position.getZ());
        BlockFrame client = data.clientWorld.resolve(position.getX(), position.getY(), position.getZ(), 0,
                BlockFrame.capture(server));
        if (client == null || client.id().equals(server.getId())) return server;
        return Block.get(client.id(), player.getLevel(), position.getX(), position.getY(), position.getZ());
    }

    private static long requiredBreakNanos(Player player, Block block, boolean grounded) {
        if (player.isCreative()) return 0;
        var held = player.getInventory().getItemInMainHand();
        double seconds = grounded
                ? block.calculateBreakTimeNotInAir(held, player)
                : block.calculateBreakTime(held, player);
        if (!Double.isFinite(seconds) || seconds < 0) return Long.MAX_VALUE;
        return (long) Math.min(Long.MAX_VALUE, Math.ceil(seconds * 1_000_000_000.0));
    }

    private static boolean breakGrounded(Player player, PlayerData data, Vector3f position) {
        if (player.isOnGround() || data.predictedOnGround) return true;
        return data.lastPosition != null
                && Math.abs(position.getY() - data.lastPosition.getY()) <= 0.08
                && MovementCheckSupport.serverGround(player, position);
    }

    private long fastBreakLeniencyMillis(PlayerData data, long clientTick) {
        long configured = Math.max(0, plugin.settings().blocksBreakLeniencyMs());
        long roundTrip = Math.max(0, data.network.stackLatencyMillis());
        long oneWay = Math.min(500, (roundTrip + 1) / 2);
        long tickCatchup = 0;
        if (data.lastTick >= 0 && clientTick > data.lastTick) {
            tickCatchup = Math.min(250, (clientTick - data.lastTick) * 50);
        }
        return Math.min(750, configured + oneWay + tickCatchup);
    }
}
