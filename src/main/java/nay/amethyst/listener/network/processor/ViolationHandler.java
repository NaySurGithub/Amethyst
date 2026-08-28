package nay.amethyst.listener.network.processor;

import nay.amethyst.check.type.CheckType;
import nay.amethyst.data.player.PlayerData;
import org.powernukkitx.Player;
import org.powernukkitx.event.Cancellable;

@FunctionalInterface
public interface ViolationHandler {
    /** Records a violation and applies its cancellation or setback policy. */
    void fail(Cancellable event, Player player, PlayerData data, CheckType check,
              double amount, String detail, boolean cancel, boolean setback);
}
