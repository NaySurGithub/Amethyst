package nay.amethyst.check.packet;

import nay.amethyst.check.scaffold.ScaffoldCheck;
import nay.amethyst.check.scaffold.WeirdPlaceCheck;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.GraceReason;
import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.RequestChunkRadiusPacket;
import org.powernukkitx.entity.Entity;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.PlayerBlockActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseOnActorActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUsePredictedResult;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseTriggerType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemReleaseInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseOnActorInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEquipmentPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.powernukkitx.Player;

public final class BadPacketCheck {
    private static final int MINIMUM_CHUNK_RADIUS = 1;
    private static final int MAXIMUM_CHUNK_RADIUS = 96;
    private static final int VOID_MARGIN = 512;
    private static final double HORIZONTAL_LIMIT = 30_000_000.0;
    private static final int VEHICLE_CLAIM_TICKS = 10;

    public Result inspect(Player player, PlayerData data, BedrockPacket packet) {
        if (packet instanceof PlayerAuthInputPacket input) return inspectAuthInput(player, data, input);
        if (packet instanceof InventoryTransactionPacket transaction) return inspectTransaction(player, data, transaction);
        if (packet instanceof ItemStackRequestPacket request) return inspectStackRequests(player, request.getRequests());
        if (packet instanceof PlayerActionPacket action) return inspectPlayerAction(player, action);
        if (packet instanceof MobEquipmentPacket equipment && !hotbarSlot(equipment.getSelectedSlot())) {
            return result(CheckType.BAD_PACKET_I, "hotbar-slot=" + equipment.getSelectedSlot());
        }
        if (packet instanceof RequestChunkRadiusPacket radius) {
            int requested = radius.getChunkRadius();
            if (requested < MINIMUM_CHUNK_RADIUS || requested > MAXIMUM_CHUNK_RADIUS) {
                return result(CheckType.BAD_PACKET_P, "radius=" + requested);
            }
        }
        return null;
    }

    private static Result inspectVehicleClaim(Player player, PlayerData data,
                                              PlayerAuthInputPacket packet) {
        boolean claims = packet.getInputData()
                .contains(PlayerAuthInputData.IS_IN_CLIENT_PREDICTED_VEHICLE);
        if (!claims) {
            data.vehicleClaimBuffer = 0;
            return null;
        }

        Entity riding = player.getRiding();
        Long predicted = packet.getClientPredictedVehicle();
        boolean matches = riding != null
                && (predicted == null || predicted == 0 || predicted == riding.getId());
        if (matches) {
            data.vehicleClaimBuffer = 0;
            return null;
        }

        if (++data.vehicleClaimBuffer < VEHICLE_CLAIM_TICKS) {
            return null;
        }
        data.vehicleClaimBuffer = 0;
        return result(CheckType.BAD_PACKET_N, riding == null
                ? "claimed vehicle " + predicted + " while riding nothing"
                : "claimed vehicle " + predicted + " while riding " + riding.getId());
    }

    private static Result inspectPositionBounds(Player player, PlayerAuthInputPacket packet) {
        Vector3f position = packet.getPosition();
        if (position == null) {
            return null;
        }
        int minimumY = player.getLevel().getDimensionData().getMinHeight() - VOID_MARGIN;
        int maximumY = player.getLevel().getDimensionData().getMaxHeight() + VOID_MARGIN;
        if (position.getY() < minimumY || position.getY() > maximumY
                || Math.abs(position.getX()) > HORIZONTAL_LIMIT
                || Math.abs(position.getZ()) > HORIZONTAL_LIMIT) {
            return result(CheckType.BAD_PACKET_O, "position=" + position);
        }
        return null;
    }

    private static Result inspectAuthInput(Player player, PlayerData data, PlayerAuthInputPacket packet) {
        long tick = packet.getClientTick();
        if (tick == 0 && data.sawNonZeroClientTick) {
            return result(CheckType.BAD_PACKET_D, "tick returned to zero");
        }
        if (tick != 0) data.sawNonZeroClientTick = true;

        Vector2f move = packet.getMoveVector();
        if (move == null || !validMoveComponent(move.getX()) || !validMoveComponent(move.getY())) {
            return result(CheckType.BAD_PACKET_H, "move-vector=" + move);
        }

        if (!data.inGrace() && !data.inGrace(GraceReason.SERVER_CORRECTION)) {
            Result bounds = inspectPositionBounds(player, packet);
            if (bounds != null) return bounds;
            Result vehicle = inspectVehicleClaim(player, data, packet);
            if (vehicle != null) return vehicle;
        }

        if (packet.getItemStackRequest() != null) {
            Result creative = inspectStackRequest(player, packet.getItemStackRequest());
            if (creative != null) return creative;
        }
        if (packet.getItemUseTransaction() != null && packet.getItemUseTransaction().getTransaction() != null) {
            ItemUseInventoryTransaction itemUse = packet.getItemUseTransaction().getTransaction();
            Result transaction = inspectItemUse(player, data, itemUse, false);
            if (transaction != null) return transaction;
            if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_ITEM_INTERACTION)
                    && !validFace(itemUse.getFace())) {
                return result(CheckType.BAD_PACKET_J, "block-face=" + itemUse.getFace());
            }
        }

        if (packet.getInputData().contains(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS)) {
            for (PlayerBlockActionData action : packet.getPlayerBlockActions()) {
                if (action.getPlayerActionType() == PlayerActionType.CREATIVE_DESTROY_BLOCK
                        && !player.isCreative()) {
                    return result(CheckType.BAD_PACKET_F, "creative destroy outside creative");
                }
                if (action.getPlayerActionType() != PlayerActionType.ABORT_DESTROY_BLOCK
                        && !validFace(action.getFacing())) {
                    return result(CheckType.BAD_PACKET_J, "block-face=" + action.getFacing());
                }
            }
        }
        return null;
    }

    private static Result inspectTransaction(Player player, PlayerData data, InventoryTransactionPacket packet) {
        if (packet.getTransaction() instanceof ItemUseOnActorInventoryTransaction actor) {
            if (!hotbarSlot(actor.getSlot())) {
                return result(CheckType.BAD_PACKET_I, "hotbar-slot=" + actor.getSlot());
            }
            if (actor.getActionType() == ItemUseOnActorActionType.ATTACK
                    && actor.getRuntimeId() == player.getId()) {
                return result(CheckType.BAD_PACKET_E, "self-attack runtime-id=" + actor.getRuntimeId());
            }
        } else if (packet.getTransaction() instanceof ItemUseInventoryTransaction itemUse) {
            return inspectItemUse(player, data, itemUse, true);
        } else if (packet.getTransaction() instanceof ItemReleaseInventoryTransaction release
                && !hotbarSlot(release.getSlot())) {
            return result(CheckType.BAD_PACKET_I, "hotbar-slot=" + release.getSlot());
        }
        return null;
    }

    private static Result inspectItemUse(Player player, PlayerData data, ItemUseInventoryTransaction transaction,
                                          boolean validateInventoryFace) {
        ScaffoldCheck.Result scaffold = ScaffoldCheck.inspect(player, data, transaction);
        if (scaffold != null) return result(CheckType.SCAFFOLD_A, scaffold.detail());
        if (transaction.getActionType() == ItemUseActionType.PLACE) {
            WeirdPlaceCheck.Result weird = WeirdPlaceCheck.inspect(player, data, transaction);
            if (weird != null) return result(CheckType.BAD_PACKET_M, weird.detail());
        }
        if (!hotbarSlot(transaction.getSlot())) {
            return result(CheckType.BAD_PACKET_I, "hotbar-slot=" + transaction.getSlot());
        }
        if (transaction.getActionType() == ItemUseActionType.DESTROY && !player.isCreative()) {
            return result(CheckType.BAD_PACKET_F, "legacy destroy transaction");
        }
        if (transaction.getActionType() == ItemUseActionType.PLACE && data.modernItemUseProtocol) {
            ItemUseTriggerType trigger = transaction.getTriggerType();
            ItemUsePredictedResult prediction = transaction.getClientInteractPrediction();
            if (trigger != ItemUseTriggerType.PLAYER_INPUT && trigger != ItemUseTriggerType.SIMULATION_TICK) {
                return result(CheckType.BAD_PACKET_I, "trigger-type=" + trigger);
            }
            if (prediction != ItemUsePredictedResult.FAILURE && prediction != ItemUsePredictedResult.SUCCESS) {
                return result(CheckType.BAD_PACKET_I, "client-prediction=" + prediction);
            }
        }
        if (validateInventoryFace && transaction.getActionType() != ItemUseActionType.USE
                && !validFace(transaction.getFace())) {
            return result(CheckType.BAD_PACKET_J, "block-face=" + transaction.getFace());
        }
        return null;
    }

    private static Result inspectStackRequests(Player player, Iterable<ItemStackRequest> requests) {
        if (requests == null) return null;
        for (ItemStackRequest request : requests) {
            Result result = inspectStackRequest(player, request);
            if (result != null) return result;
        }
        return null;
    }

    private static Result inspectStackRequest(Player player, ItemStackRequest request) {
        if (request == null || request.getActions() == null) return null;
        for (ItemStackRequestAction action : request.getActions()) {
            if (action != null && action.getType() == ItemStackRequestActionType.CRAFT_CREATIVE
                    && !player.isCreative()) {
                return result(CheckType.BAD_PACKET_G, "creative craft outside creative");
            }
        }
        return null;
    }

    private static Result inspectPlayerAction(Player player, PlayerActionPacket packet) {
        PlayerActionType action = packet.getAction();
        if (legacyBreakAction(action)
                || action == PlayerActionType.CREATIVE_DESTROY_BLOCK && !player.isCreative()) {
            return result(CheckType.BAD_PACKET_F, "invalid break channel=" + action);
        }
        return null;
    }

    private static boolean legacyBreakAction(PlayerActionType action) {
        return action == PlayerActionType.START_DESTROY_BLOCK
                || action == PlayerActionType.CRACK_BLOCK
                || action == PlayerActionType.CONTINUE_DESTROY_BLOCK
                || action == PlayerActionType.PREDICT_DESTROY_BLOCK
                || action == PlayerActionType.ABORT_DESTROY_BLOCK
                || action == PlayerActionType.STOP_DESTROY_BLOCK;
    }

    private static boolean validMoveComponent(float value) {
        return Float.isFinite(value) && value >= -1.001f && value <= 1.001f;
    }

    private static boolean hotbarSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    private static boolean validFace(int face) {
        return face >= 0 && face <= 5;
    }

    private static Result result(CheckType check, String detail) {
        return new Result(check, detail);
    }

    public record Result(CheckType check, String detail) {
    }
}
