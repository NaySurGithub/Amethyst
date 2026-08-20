package nay.amethyst.network.session;

import nay.amethyst.packet.movement.MovementPacketSnapshot;
import nay.amethyst.packet.movement.MovementPreValidationResult;
import nay.amethyst.packet.movement.MovementPreValidator;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.function.Consumer;

public final class MovementSessionInterceptor implements BedrockPacketHandler {
    private final BedrockPacketHandler original;
    private final MovementPreValidator validator;
    private final Consumer<MovementPreValidationResult> rejectionHandler;
    private long lastReportedRejectionNanos;

    public MovementSessionInterceptor(BedrockPacketHandler original,
                                      MovementPreValidator validator,
                                      Consumer<MovementPreValidationResult> rejectionHandler) {
        this.original = original;
        this.validator = validator;
        this.rejectionHandler = rejectionHandler;
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
