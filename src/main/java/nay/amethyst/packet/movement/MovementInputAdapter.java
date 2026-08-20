package nay.amethyst.packet.movement;

import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.MovementInputFlag;
import nay.amethyst.simulation.movement.MovementInputFrame;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;

import java.util.EnumMap;
import java.util.Map;

public final class MovementInputAdapter {
    private static final Map<MovementInputFlag, PlayerAuthInputData> FLAGS = flags();

    private MovementInputAdapter() {
    }

    public static MovementInputFrame capture(PlayerAuthInputPacket packet) {
        Vector3f position = packet.getPosition();
        Vector3f delta = packet.getPosDelta();
        Vector3f rotation = packet.getPlayerRotation();
        Vector2f movement = packet.getMoveVector();
        MovementInputFrame.Builder builder = MovementInputFrame.builder()
                .tick(packet.getClientTick())
                .position(vector(position))
                .delta(vector(delta))
                .rotation(rotation(rotation))
                .moveVector(movement == null ? 0.0f : movement.getX(),
                        movement == null ? 0.0f : movement.getY());
        for (Map.Entry<MovementInputFlag, PlayerAuthInputData> entry : FLAGS.entrySet()) {
            if (packet.getInputData().contains(entry.getValue())) {
                builder.flag(entry.getKey());
            }
        }
        return builder.build();
    }

    private static Map<MovementInputFlag, PlayerAuthInputData> flags() {
        Map<MovementInputFlag, PlayerAuthInputData> flags = new EnumMap<>(MovementInputFlag.class);
        flags.put(MovementInputFlag.HORIZONTAL_COLLISION, PlayerAuthInputData.HORIZONTAL_COLLISION);
        flags.put(MovementInputFlag.VERTICAL_COLLISION, PlayerAuthInputData.VERTICAL_COLLISION);
        flags.put(MovementInputFlag.START_FLYING, PlayerAuthInputData.START_FLYING);
        flags.put(MovementInputFlag.STOP_FLYING, PlayerAuthInputData.STOP_FLYING);
        flags.put(MovementInputFlag.SNEAKING, PlayerAuthInputData.SNEAKING);
        flags.put(MovementInputFlag.SPRINT_DOWN, PlayerAuthInputData.SPRINT_DOWN);
        flags.put(MovementInputFlag.START_SPRINTING, PlayerAuthInputData.START_SPRINTING);
        flags.put(MovementInputFlag.STOP_SPRINTING, PlayerAuthInputData.STOP_SPRINTING);
        flags.put(MovementInputFlag.START_SNEAKING, PlayerAuthInputData.START_SNEAKING);
        flags.put(MovementInputFlag.STOP_SNEAKING, PlayerAuthInputData.STOP_SNEAKING);
        flags.put(MovementInputFlag.START_SWIMMING, PlayerAuthInputData.START_SWIMMING);
        flags.put(MovementInputFlag.STOP_SWIMMING, PlayerAuthInputData.STOP_SWIMMING);
        flags.put(MovementInputFlag.START_CRAWLING, PlayerAuthInputData.START_CRAWLING);
        flags.put(MovementInputFlag.STOP_CRAWLING, PlayerAuthInputData.STOP_CRAWLING);
        flags.put(MovementInputFlag.SNEAK_DOWN, PlayerAuthInputData.SNEAK_DOWN);
        flags.put(MovementInputFlag.START_JUMPING, PlayerAuthInputData.START_JUMPING);
        flags.put(MovementInputFlag.JUMPING, PlayerAuthInputData.JUMPING);
        flags.put(MovementInputFlag.START_GLIDING, PlayerAuthInputData.START_GLIDING);
        flags.put(MovementInputFlag.STOP_GLIDING, PlayerAuthInputData.STOP_GLIDING);
        flags.put(MovementInputFlag.JUMP_PRESSED_RAW, PlayerAuthInputData.JUMP_PRESSED_RAW);
        return Map.copyOf(flags);
    }

    private static FloatVector vector(Vector3f vector) {
        if (vector == null) {
            return new FloatVector(Float.NaN, Float.NaN, Float.NaN);
        }
        return new FloatVector(vector.getX(), vector.getY(), vector.getZ());
    }

    private static FloatVector rotation(Vector3f vector) {
        if (vector == null) {
            return new FloatVector(Float.NaN, Float.NaN, Float.NaN);
        }
        return new FloatVector(vector.getX(), vector.getZ(), vector.getY());
    }
}
