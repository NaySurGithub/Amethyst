package nay.amethyst.check.inventory;

import nay.amethyst.data.player.GraceReason;
import nay.amethyst.data.player.PlayerData;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.packet.ItemStackRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

public final class InventoryMoveCheck {
    public void handleRequest(PlayerData data, ItemStackRequestPacket packet, long now) {
        if (packet.getRequests() == null || packet.getRequests().isEmpty()
                || data.inGrace() || data.inGrace(GraceReason.VELOCITY)) return;
        data.inventoryMovePrepared = data.lastDirectionalInput;
        data.inventoryMoveRequestNanos = now;
    }

    public Result handleMovement(PlayerData data, PlayerAuthInputPacket packet, long now,
                                 double inputThreshold, long requestWindowMillis, int bufferThreshold) {
        double magnitude = inputMagnitude(packet);
        double drivenMovement = drivenMovement(data, packet.getPosition());
        boolean directional = magnitude > inputThreshold || drivenMovement > inputThreshold;
        boolean requestFresh = data.inventoryMovePrepared
                && now - data.inventoryMoveRequestNanos <= requestWindowMillis * 1_000_000L;
        boolean suspicious = requestFresh && directional && !data.inGrace(GraceReason.VELOCITY);

        data.inventoryMovePrepared = false;
        data.lastDirectionalInput = directional;
        if (suspicious) data.inventoryMoveBuffer++;
        else data.inventoryMoveBuffer = Math.max(0, data.inventoryMoveBuffer - 1);

        if (data.inventoryMoveBuffer < bufferThreshold) return new Result(false, Math.max(magnitude, drivenMovement));
        data.inventoryMoveBuffer = Math.max(0, bufferThreshold - 1);
        return new Result(true, Math.max(magnitude, drivenMovement));
    }

    private static double inputMagnitude(PlayerAuthInputPacket packet) {
        return Math.max(magnitude(packet.getRawMoveVector()),
                Math.max(magnitude(packet.getAnalogMoveVector()), magnitude(packet.getMoveVector())));
    }

    private static double magnitude(Vector2f vector) {
        return vector == null ? 0 : Math.hypot(vector.getX(), vector.getY());
    }

    private static double drivenMovement(PlayerData data, Vector3f position) {
        if (data.lastPosition == null || position == null) return 0;
        double actual = Math.hypot(position.getX() - data.lastPosition.getX(),
                position.getZ() - data.lastPosition.getZ());
        double passive = data.predictedVelocity == null ? 0 : data.predictedVelocity.horizontalLength();
        return Math.max(0, actual - passive - 0.03);
    }

    public record Result(boolean failed, double inputMagnitude) {
    }
}
