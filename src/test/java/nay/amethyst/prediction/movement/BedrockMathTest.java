package nay.amethyst.prediction.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedrockMathTest {

    private static final float DELTA = 1.0e-3f;

    @Test
    void sin_atRemarkableAngles_matchesExpectedValues() {
        assertEquals(0.0f, BedrockMath.sin(0.0f), DELTA);
        assertEquals(1.0f, BedrockMath.sin((float) (Math.PI / 2.0)), DELTA);
        assertEquals(0.0f, BedrockMath.sin((float) Math.PI), DELTA);
        assertEquals(-1.0f, BedrockMath.sin((float) (3.0 * Math.PI / 2.0)), DELTA);
    }

    @Test
    void cos_atRemarkableAngles_matchesExpectedValues() {
        assertEquals(1.0f, BedrockMath.cos(0.0f), DELTA);
        assertEquals(0.0f, BedrockMath.cos((float) (Math.PI / 2.0)), DELTA);
        assertEquals(-1.0f, BedrockMath.cos((float) Math.PI), DELTA);
        assertEquals(0.0f, BedrockMath.cos((float) (3.0 * Math.PI / 2.0)), DELTA);
    }

    @Test
    void sin_periodicAcrossTwoPi_wrapsToSameValue() {
        float baseAngle = 0.7f;
        float wrappedAngle = baseAngle + (float) (2.0 * Math.PI);

        assertEquals(BedrockMath.sin(baseAngle), BedrockMath.sin(wrappedAngle), DELTA);
        assertEquals(BedrockMath.cos(baseAngle), BedrockMath.cos(wrappedAngle), DELTA);
    }

    @Test
    void sinSquaredPlusCosSquared_atArbitraryAngle_equalsOne() {
        float angle = 1.234f;
        float sinValue = BedrockMath.sin(angle);
        float cosValue = BedrockMath.cos(angle);

        assertEquals(1.0f, sinValue * sinValue + cosValue * cosValue, DELTA);
    }
}
