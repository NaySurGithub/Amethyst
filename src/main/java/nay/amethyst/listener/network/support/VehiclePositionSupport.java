package nay.amethyst.listener.network.support;

import org.cloudburstmc.math.vector.Vector3f;
import org.powernukkitx.Player;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.item.EntityBoat;
import nay.amethyst.prediction.common.Vec3;

public final class VehiclePositionSupport {
    private VehiclePositionSupport() {
    }

    public static Vec3 fromPacket(Entity vehicle, Player player, Vector3f position) {
        if (vehicle instanceof EntityBoat boat) {
            return new Vec3(position.getX(), position.getY() - boat.getBaseOffset(), position.getZ());
        }
        int seat = vehicle.getPassengers().indexOf(player);
        if (seat >= 0) {
            var offset = vehicle.getSeatOffsetFor(seat, player);
            return new Vec3(position.getX() - offset.x, position.getY() - offset.y, position.getZ() - offset.z);
        }
        return new Vec3(position.getX(), position.getY(), position.getZ());
    }
}
