package nay.amethyst.api;

import nay.amethyst.check.type.CheckType;
import org.powernukkitx.Player;
import org.powernukkitx.event.Cancellable;
import org.powernukkitx.event.HandlerList;
import org.powernukkitx.event.player.PlayerEvent;

/**
 * Fired whenever a check flags a player, before the alert is sent. Cancelling it suppresses the alert
 * and the violation, which is how another plugin exempts a case Amethyst cannot know about.
 */
public class PlayerViolationEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final CheckType check;
    private final double violations;
    private final String detail;

    public PlayerViolationEvent(Player player, CheckType check, double violations, String detail) {
        this.player = player;
        this.check = check;
        this.violations = violations;
        this.detail = detail;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public CheckType getCheck() {
        return check;
    }

    /** Violation level for this check after this flag. */
    public double getViolations() {
        return violations;
    }

    /** The values behind the flag, as written in the alert. */
    public String getDetail() {
        return detail;
    }
}
