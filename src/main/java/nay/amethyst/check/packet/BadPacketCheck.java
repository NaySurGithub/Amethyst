package nay.amethyst.check.packet;

import nay.amethyst.check.scaffold.ScaffoldCheck;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.math.vector.Vector2f;
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
    public Result inspect(Player player, PlayerData data, BedrockPacket packet) {
        if (packet instanceof PlayerAuthInputPacket input) return inspectAuthInput(player, data, input);
        if (packet instanceof InventoryTransactionPacket transaction) return inspectTransaction(player, data, transaction);
        if (packet instanceof ItemStackRequestPacket request) return inspectStackRequests(player, request.getRequests());
        if (packet instanceof PlayerActionPacket action) return inspectPlayerAction(player, action);
        if (packet instanceof MobEquipmentPacket equipment && !hotbarSlot(equipment.getSelectedSlot())) {
            return result(CheckType.BAD_PACKET_I, "hotbar-slot=" + equipment.getSelectedSlot());
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
