package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloatVectorTest {

    private static final float DELTA = 1.0e-5f;

    @Test
    void length_and_horizontalLengthSquared_forZeroAndNonZeroVectors() {
        assertEquals(0.0f, FloatVector.ZERO.length(), DELTA);
        assertEquals(0.0f, FloatVector.ZERO.horizontalLengthSquared(), DELTA);

        FloatVector vector = new FloatVector(3.0f, 4.0f, 0.0f);
        assertEquals(5.0f, vector.length(), DELTA);
        assertEquals(9.0f, vector.horizontalLengthSquared(), DELTA);

        FloatVector negativeVector = new FloatVector(-3.0f, 0.0f, -4.0f);
        assertEquals(5.0f, negativeVector.length(), DELTA);
        assertEquals(25.0f, negativeVector.horizontalLengthSquared(), DELTA);
    }

    @Test
    void add_subtract_multiply_produceExpectedComponents() {
        FloatVector first = new FloatVector(1.0f, 2.0f, 3.0f);
        FloatVector second = new FloatVector(4.0f, -1.0f, 0.5f);

        FloatVector sum = first.add(second);
        assertEquals(5.0f, sum.x(), DELTA);
        assertEquals(1.0f, sum.y(), DELTA);
        assertEquals(3.5f, sum.z(), DELTA);

        FloatVector difference = first.subtract(second);
        assertEquals(-3.0f, difference.x(), DELTA);
        assertEquals(3.0f, difference.y(), DELTA);
        assertEquals(2.5f, difference.z(), DELTA);

        FloatVector scaled = first.multiply(2.0f);
        assertEquals(2.0f, scaled.x(), DELTA);
        assertEquals(4.0f, scaled.y(), DELTA);
        assertEquals(6.0f, scaled.z(), DELTA);

        FloatVector scaledPerAxis = first.multiply(1.0f, 0.0f, -1.0f);
        assertEquals(1.0f, scaledPerAxis.x(), DELTA);
        assertEquals(0.0f, scaledPerAxis.y(), DELTA);
        assertEquals(-3.0f, scaledPerAxis.z(), DELTA);
    }
}
