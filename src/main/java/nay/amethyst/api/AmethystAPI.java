package nay.amethyst.api;

import nay.amethyst.AmethystPlugin;
import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import org.powernukkitx.Player;

/**
 * Stable entry point for other plugins to read and clear a player's violations.
 */
public final class AmethystAPI {

    private AmethystAPI() {
    }

    /**
     * Clears every violation of the player across all checks.
     *
     * @return true if the player was tracked and reset, false otherwise.
     */
    public static boolean resetViolations(Player player) {
        PlayerData data = data(player);
        if (data == null) {
            return false;
        }
        data.violations.clear();
        return true;
    }

    /**
     * Clears the player's violations for a single check.
     *
     * @return true if the player was tracked, false otherwise.
     */
    public static boolean resetViolations(Player player, CheckType check) {
        PlayerData data = data(player);
        if (data == null || check == null) {
            return false;
        }
        data.violations.remove(check.id());
        return true;
    }

    /**
     * Clears the player's violations for a check referenced by its id (e.g. "Reach-A"), case-insensitive.
     *
     * @return true if the player was tracked and the id matched a known check, false otherwise.
     */
    public static boolean resetViolations(Player player, String checkId) {
        if (checkId == null) {
            return false;
        }
        for (CheckType check : CheckType.values()) {
            if (check.id().equalsIgnoreCase(checkId)) {
                return resetViolations(player, check);
            }
        }
        return false;
    }

    /**
     * @return the current violation count of the player for the given check, or 0 if untracked.
     */
    public static double getViolations(Player player, CheckType check) {
        PlayerData data = data(player);
        if (data == null || check == null) {
            return 0;
        }
        return data.violations.getOrDefault(check.id(), 0.0);
    }

    private static PlayerData data(Player player) {
        AmethystPlugin plugin = AmethystPlugin.getInstance();
        return plugin == null ? null : plugin.getPlayerData(player);
    }
}
