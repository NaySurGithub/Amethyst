package nay.amethyst.packet.movement;

import nay.amethyst.prediction.common.Vec3;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.Set;

public record MovementPacketSnapshot(
        long clientTick,
        Vec3 position,
        Vec3 reportedDelta,
        float pitch,
        float yaw,
        float headYaw,
        Set<PlayerAuthInputData> inputFlags
) {
    public MovementPacketSnapshot {
        inputFlags = Set.copyOf(inputFlags);
    }

    public static MovementPacketSnapshot capture(PlayerAuthInputPacket packet) {
        Vector3f rotation = packet.getPlayerRotation();
        return new MovementPacketSnapshot(
                packet.getClientTick(),
                vector(packet.getPosition()),
                vector(packet.getPosDelta()),
                rotation == null ? Float.NaN : rotation.getX(),
                rotation == null ? Float.NaN : rotation.getY(),
                rotation == null ? Float.NaN : rotation.getZ(),
                packet.getInputData() == null ? Set.of() : packet.getInputData()
        );
    }

    public boolean has(PlayerAuthInputData flag) {
        return inputFlags.contains(flag);
    }

    private static Vec3 vector(Vector3f value) {
        if (value == null) return new Vec3(Double.NaN, Double.NaN, Double.NaN);
        return new Vec3(value.getX(), value.getY(), value.getZ());
    }
}
