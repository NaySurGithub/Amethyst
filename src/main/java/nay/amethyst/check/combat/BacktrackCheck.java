package nay.amethyst.check.combat;

import nay.amethyst.data.player.PlayerData;
import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.cloudburstmc.protocol.bedrock.packet.MoveActorAbsolutePacket;

/**
 * Vanilla clients only send actor movement for the vehicle they are currently controlling.
 * Backtrack modules replay withheld entity movement towards the server, which surfaces as movement
 * for an entity the player does not ride.
 */
public final class BacktrackCheck {
    private static final long DISMOUNT_GRACE_NANOS = 1_500_000_000L;

    public Result inspect(Player player, PlayerData data, MoveActorAbsolutePacket packet, long now) {
        long actorRuntimeId = packet.getMoveData().getActorRuntimeID();
        Entity vehicle = player.getRiding();

        if (vehicle == null) {
            if (now - data.lastRidingNanos <= DISMOUNT_GRACE_NANOS) {
                return Result.CLEAN;
            }
            return new Result(true, "no vehicle, actor=" + actorRuntimeId);
        }

        if (vehicle.getId() == actorRuntimeId) {
            return Result.CLEAN;
        }

        return new Result(true, "actor=" + actorRuntimeId + " vehicle=" + vehicle.getId());
    }

    public record Result(boolean failed, String detail) {
        static final Result CLEAN = new Result(false, "");
    }
}
