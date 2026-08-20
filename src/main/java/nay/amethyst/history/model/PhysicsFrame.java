package nay.amethyst.history.model;

public record PhysicsFrame(
        double movementSpeed,
        double underwaterMovementSpeed,
        double lavaMovementSpeed,
        double knockbackResistance,
        double gravity,
        int jumpBoostAmplifier,
        int levitationAmplifier,
        boolean slowFalling,
        boolean sprinting,
        boolean swimming,
        boolean gliding,
        double width,
        double height,
        double scale,
        double baseOffset,
        double eyeHeight
) {
}
