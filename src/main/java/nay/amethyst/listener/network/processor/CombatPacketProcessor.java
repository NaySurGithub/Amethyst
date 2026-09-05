package nay.amethyst.listener.network.processor;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.prediction.combat.CombatPredictionResult;
import nay.amethyst.prediction.combat.CombatPredictor;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.ItemUseOnActorActionType;
import org.cloudburstmc.protocol.bedrock.data.payload.inventory.transaction.data.ItemUseOnActorInventoryTransaction;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.event.server.PacketReceiveEvent;

import java.util.Map;
import java.util.UUID;

/** Processes attack transactions against the compensated combat model. */
public final class CombatPacketProcessor {
    private final AmethystPlugin plugin;
    private final Map<UUID, PlayerData> players;
    private final ViolationHandler violations;
    private final CombatPredictor predictor = new CombatPredictor();

    /** Creates a combat processor backed by the shared player state. */
    public CombatPacketProcessor(AmethystPlugin plugin, Map<UUID, PlayerData> players,
                                 ViolationHandler violations) {
        this.plugin = plugin;
        this.players = players;
        this.violations = violations;
    }

    /** Validates an attack transaction when the packet contains one. */
    public void inspectAttack(PacketReceiveEvent event, Player player,
                              InventoryTransactionPacket packet) {
        if (!(packet.getTransaction() instanceof ItemUseOnActorInventoryTransaction transaction)
                || transaction.getActionType() != ItemUseOnActorActionType.ATTACK) {
            return;
        }
        PlayerData data = players.get(player.getUniqueId());
        if (data == null) {
            return;
        }
        Entity target = player.getLevel().getEntity(transaction.getRuntimeId());
        if (target == null) {
            event.setCancelled();
            return;
        }
        if (target == player) {
            violations.fail(event, player, data, CheckType.KILL_AURA_A, 1,
                    "invalid target", true, false);
            return;
        }
        if (data.inGrace()) {
            return;
        }
        var clientEntity = data.clientEntities.view(target.getId());
        if (clientEntity != null && clientEntity.ticksSinceTeleport() <= 10) {
            return;
        }

        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        var settings = plugin.settings();
        double expansion = Math.max(0, settings.combatBboxExpansion());
        double leniency = Math.max(0, settings.combatReachLeniency());
        int steps = Math.max(1, settings.combatInterpolationSteps());
        double maximumAngle = settings.combatMaximumAttackAngle();
        double closeRange = Math.max(0, settings.combatCloseRangeFallback());
        double closeAngle = Math.max(0, settings.combatCloseRangeAngle());
        CombatPredictionResult result = predictor.predict(player, data, target,
                expansion, leniency, steps, maximumAngle, closeRange, closeAngle, data.touchInput);
        if (result.rawDistance() > CombatPredictor.SURVIVAL_REACH + leniency) {
            violations.fail(event, player, data, CheckType.REACH_A, 1,
                    "raw=" + NetworkCheckSupport.format(result.rawDistance())
                            + " ray=" + NetworkCheckSupport.format(result.rayDistance()), true, false);
        } else if (target instanceof Player
                && (!result.raycastHit() || result.angle() > maximumAngle)) {
            violations.fail(event, player, data, CheckType.HITBOX_A, 1,
                    "angle=" + NetworkCheckSupport.format(result.angle())
                            + " raw=" + NetworkCheckSupport.format(result.rawDistance()), true, false);
        } else if (target instanceof Player && !result.valid()) {
            violations.fail(event, player, data, CheckType.REACH_A, 1,
                    "ray=" + NetworkCheckSupport.format(result.rayDistance())
                            + " raw=" + NetworkCheckSupport.format(result.rawDistance()), true, false);
        }
    }
}
