package nay.amethyst.network;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.ClientCacheStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.cloudburstmc.protocol.bedrock.packet.TickSyncPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.function.Consumer;

public final class MovementSessionInterceptor implements BedrockPacketHandler {
    private final BedrockPacketHandler original;
    private final MovementPreValidator validator;
    private final Consumer<MovementPreValidationResult> rejectionHandler;
    private final Consumer<BedrockPacket> forwarded;
    private long lastReportedRejectionNanos;

    public MovementSessionInterceptor(BedrockPacketHandler original,
                                      MovementPreValidator validator,
                                      Consumer<MovementPreValidationResult> rejectionHandler,
                                      Consumer<BedrockPacket> forwarded) {
        this.original = original;
        this.validator = validator;
        this.rejectionHandler = rejectionHandler;
        this.forwarded = forwarded;
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet) {
        if (packet instanceof PlayerAuthInputPacket input) {
            MovementPreValidationResult result = validator.validate(
                    MovementPacketSnapshot.capture(input));
            if (!result.accepted()) {
                long now = System.nanoTime();
                if (result.check() != null
                        && now - lastReportedRejectionNanos >= 250_000_000L) {
                    lastReportedRejectionNanos = now;
                    rejectionHandler.accept(result);
                }
                return PacketSignal.HANDLED;
            }
        }
        // numbered in arrival order, for the packets the server thread handles in that order; the
        // three the network thread handles itself are not, a probe answer being the thing ordered
        if (!(packet instanceof NetworkStackLatencyPacket) && !(packet instanceof TickSyncPacket)
                && !(packet instanceof ClientCacheStatusPacket)) {
            forwarded.accept(packet);
        }
        return original.handlePacket(packet);
    }

    @Override
    public void onDisconnect(String reason) {
        original.onDisconnect(reason);
    }

    public BedrockPacketHandler original() {
        return original;
    }

    public MovementPreValidator validator() {
        return validator;
    }
}
