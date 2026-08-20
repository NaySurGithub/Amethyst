package nay.amethyst.packet.movement;

import nay.amethyst.check.type.CheckType;
import nay.amethyst.prediction.common.Vec3;


public final class MovementPreValidator {
    private static final double POSITION_EPSILON_SQUARED = 1.0E-8;

    private MovementPacketSnapshot previous;
    private boolean sawNonZeroTick;
    private final Runnable inputCounter;

    public MovementPreValidator(Runnable inputCounter) {
        this.inputCounter = inputCounter;
    }

    public MovementPreValidationResult validate(MovementPacketSnapshot snapshot) {
        MovementPreValidationResult structural = validateStructure(snapshot);
        if (!structural.accepted()) {
            return structural;
        }
        inputCounter.run();

        if (previous != null && snapshot.clientTick() <= previous.clientTick()) {
            if (snapshot.position().distanceSquared(previous.position()) > POSITION_EPSILON_SQUARED) {
                return MovementPreValidationResult.reject(CheckType.BAD_PACKET_B, 2,
                        "movement on stale tick=" + snapshot.clientTick()
                                + " last=" + previous.clientTick());
            }
            return MovementPreValidationResult.blockSilently();
        }

        accept(snapshot);
        return MovementPreValidationResult.accept();
    }

    public void reset() {
        previous = null;
        sawNonZeroTick = false;
    }

    private MovementPreValidationResult validateStructure(MovementPacketSnapshot snapshot) {
        if (!finite(snapshot.position()) || !finite(snapshot.reportedDelta())
                || !Float.isFinite(snapshot.pitch()) || !Float.isFinite(snapshot.yaw())
                || !Float.isFinite(snapshot.headYaw())) {
            return MovementPreValidationResult.reject(CheckType.BAD_PACKET_A, 4,
                    "non-finite movement");
        }
        if (Math.abs(snapshot.pitch()) > 90.01f || Math.abs(snapshot.yaw()) > 3600f
                || Math.abs(snapshot.headYaw()) > 3600f) {
            return MovementPreValidationResult.reject(CheckType.BAD_PACKET_B, 2,
                    "invalid rotation");
        }
        if (sawNonZeroTick && snapshot.clientTick() == 0) {
            return MovementPreValidationResult.reject(CheckType.BAD_PACKET_D, 2,
                    "tick returned to zero");
        }
        return MovementPreValidationResult.accept();
    }

    private void accept(MovementPacketSnapshot snapshot) {
        previous = snapshot;
        if (snapshot.clientTick() != 0) sawNonZeroTick = true;
    }

    private static boolean finite(Vec3 value) {
        return Double.isFinite(value.x()) && Double.isFinite(value.y())
                && Double.isFinite(value.z());
    }

}
