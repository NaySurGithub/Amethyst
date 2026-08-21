package nay.amethyst.config;

import org.powernukkitx.utils.Config;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public record AmethystSettings(
        double inventoryMoveInputThreshold,
        long inventoryMoveRequestWindowMs,
        int inventoryMoveBufferThreshold,
        double predictionTolerance,
        double predictionBufferThreshold,
        double predictionBufferDecay,
        double vehicleTolerance,
        double vehicleBufferThreshold,
        double vehicleBufferDecay,
        int timerWarmupPackets,
        int timerViolationSamples,
        int maxPacketActions,
        double combatBboxExpansion,
        double combatReachLeniency,
        int combatInterpolationSteps,
        double combatMaximumAttackAngle,
        double combatCloseRangeFallback,
        double combatCloseRangeAngle,
        int combatCpsLimit,
        int combatTouchCpsLimit,
        double blocksMaxReach,
        long blocksBreakLeniencyMs,
        double setbackViolations,
        boolean devLogs,
        Set<String> disabledChecks
) {
    public boolean disabled(String checkId) {
        return disabledChecks.contains(checkId.toLowerCase(Locale.ROOT));
    }

    public static AmethystSettings load(Config config) {
        return new AmethystSettings(
                config.getDouble("inventory-move.input-threshold", 0.075),
                config.getLong("inventory-move.request-window-ms", 750),
                config.getInt("inventory-move.buffer-threshold", 2),
                config.getDouble("prediction.tolerance", 0.05),
                config.getDouble("prediction.buffer-threshold", 0.6),
                config.getDouble("prediction.buffer-decay", 0.08),
                config.getDouble("vehicle.tolerance", 0.16),
                config.getDouble("vehicle.buffer-threshold", 0.4),
                config.getDouble("vehicle.buffer-decay", 0.1),
                config.getInt("timer.warmup-packets", 40),
                config.getInt("timer.violation-samples", 8),
                config.getInt("max-packet-actions", 12),
                config.getDouble("combat.bbox-expansion", 0.1),
                config.getDouble("combat.reach-leniency", 0),
                config.getInt("combat.interpolation-steps", 10),
                config.getDouble("combat.maximum-attack-angle", 85),
                config.getDouble("combat.close-range-fallback", 1.75),
                config.getDouble("combat.close-range-angle", 60),
                config.getInt("combat.cps-limit", 24),
                config.getInt("combat.touch-cps-limit", 24),
                config.getDouble("blocks.max-reach", 7),
                config.getLong("blocks.break-leniency-ms", 75),
                config.getDouble("setback-violations", 2.0),
                config.getBoolean("dev-logs", false),
                disabledChecks(config));
    }

    private static Set<String> disabledChecks(Config config) {
        Set<String> disabled = new HashSet<>();
        for (String entry : config.getStringList("disabled-checks")) {
            if (entry != null && !entry.isBlank()) {
                disabled.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(disabled);
    }
}
