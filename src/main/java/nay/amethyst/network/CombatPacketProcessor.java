package nay.amethyst.network;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.BacktrackStats;
import nay.amethyst.check.CheckType;
import nay.amethyst.player.PlayerData;
import nay.amethyst.prediction.CombatPredictionResult;
import nay.amethyst.prediction.CombatPredictor;
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
        } else if (target instanceof Player) {
            inspectBacktrack(event, player, data, target, expansion, leniency, steps, maximumAngle,
                    closeRange, closeAngle);
        }
    }

    /** Samples an accepted hit: was the target moving away, and did the hit need an older position. */
    private void inspectBacktrack(PacketReceiveEvent event, Player player, PlayerData data, Entity target,
                                  double expansion, double leniency, int steps, double maximumAngle,
                                  double closeRange, double closeAngle) {
        var settings = plugin.settings();
        if (settings.disabledCheck(CheckType.BACKTRACK_B.id())) return;
        CombatPredictor.Rewind rewind = predictor.rewind(data, target.getId(), expansion, leniency,
                steps, maximumAngle, closeRange, closeAngle, data.touchInput);
        if (rewind == null) return;
        data.backtrack.countHit();
        if (!rewind.receding()) return;
        data.backtrack.add(new BacktrackStats.Sample(rewind.rewound(), rewind.delayTicks(), rewind.gain()),
                settings.backtrackWindow());
        if (rewind.rewound() && settings.backtrackLogSamples()) {
            plugin.getLogger().info("[Amethyst] backtrack sample " + player.getName()
                    + " -> " + target.getName()
                    + " delay=" + NetworkCheckSupport.format(rewind.delayTicks() * 50.0) + "ms"
                    + " gain=" + NetworkCheckSupport.format(rewind.gain())
                    + " rtt=" + data.network.stackLatencyMillis() + "ms"
                    + " | " + data.backtrack.describe());
        }
        BacktrackStats.Verdict verdict = data.backtrack.evaluate(settings.backtrackMinimumSamples(),
                settings.backtrackRatio());
        if (verdict.failed()) {
            violations.fail(event, player, data, CheckType.BACKTRACK_B, 1, verdict.detail(), false, false);
        }
    }
}
