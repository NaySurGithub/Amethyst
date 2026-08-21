package nay.amethyst.diagnostics;

import nay.amethyst.check.type.CheckType;
import org.powernukkitx.Player;

public final class AlertFormatter {
    public String format(Player player, CheckType type, double violations, String detail) {
        String experimental = type.experimental() ? "§c*" : "";
        String suffix = detail == null || detail.isEmpty() ? "" : " §7" + detail;
        return "§u[Amethyst] §f" + player.getName() + " §ufailed §f" + type.id() + experimental
                + " §f(x§c" + String.format("%.1f", violations) + "§f)" + suffix;
    }
}
