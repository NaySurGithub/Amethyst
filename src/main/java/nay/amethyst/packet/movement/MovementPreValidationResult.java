package nay.amethyst.packet.movement;

import nay.amethyst.check.type.CheckType;

public record MovementPreValidationResult(
        boolean accepted,
        CheckType check,
        double violationAmount,
        String detail
) {
    private static final MovementPreValidationResult ACCEPTED =
            new MovementPreValidationResult(true, null, 0, "");
    private static final MovementPreValidationResult BLOCKED =
            new MovementPreValidationResult(false, null, 0, "");

    public static MovementPreValidationResult accept() {
        return ACCEPTED;
    }

    public static MovementPreValidationResult blockSilently() {
        return BLOCKED;
    }

    public static MovementPreValidationResult reject(CheckType check, double amount, String detail) {
        return new MovementPreValidationResult(false, check, amount, detail);
    }
}
