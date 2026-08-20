package nay.amethyst.listener.network.support;

import nay.amethyst.data.player.PlayerData;
import org.powernukkitx.Player;

public final class NetworkCheckSupport {
    private NetworkCheckSupport() {
    }

    public static long ping(Player player) {
        try {
            return Math.max(0, player.getPing());
        } catch (RuntimeException ignored) {
            // The RakNet child channel may disappear while the last packet is being drained.
            return 0;
        }
    }

    public static int velocityWindow(Player player, PlayerData data) {
        long latency = Math.max(ping(player), data.network.stackLatencyMillis());
        return Math.max(6, Math.min(40, 6 + (int) Math.ceil(latency / 50.0)));
    }

    public static long velocityMinimumAgeNanos(Player player, PlayerData data) {
        long latency = Math.max(ping(player), data.network.stackLatencyMillis());
        long millis = Math.max(750, latency * 3L + 250);
        return millis * 1_000_000L;
    }

    public static boolean versionAtLeast(String version, int major, int minor, int patch) {
        if (version == null) return false;
        String[] parts = version.split("\\.");
        int[] required = {major, minor, patch};
        try {
            for (int index = 0; index < required.length; index++) {
                int actual = 0;
                if (index < parts.length) actual = Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
                if (actual != required[index]) return actual > required[index];
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static String format(double value) {
        return String.format("%.3f", value);
    }
}
