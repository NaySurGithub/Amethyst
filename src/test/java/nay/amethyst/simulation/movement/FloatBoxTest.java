package nay.amethyst.simulation.movement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloatBoxTest {

    private static final float DELTA = 1.0e-6f;

    @Test
    void extend_positiveDelta_growsMaxBoundsOnly() {
        FloatBox box = new FloatBox(0.0f, 0.0f, 0.0f, 1.0f, 2.0f, 1.0f);
        FloatVector movement = new FloatVector(0.5f, 0.0f, 0.0f);

        FloatBox extended = box.extend(movement);

        assertEquals(0.0f, extended.minX(), DELTA);
        assertEquals(1.5f, extended.maxX(), DELTA);
        assertEquals(0.0f, extended.minY(), DELTA);
        assertEquals(2.0f, extended.maxY(), DELTA);
        assertEquals(0.0f, extended.minZ(), DELTA);
        assertEquals(1.0f, extended.maxZ(), DELTA);
    }

    @Test
    void extend_negativeDelta_growsMinBoundsOnly() {
        FloatBox box = new FloatBox(0.0f, 0.0f, 0.0f, 1.0f, 2.0f, 1.0f);
        FloatVector movement = new FloatVector(-0.5f, -1.0f, 0.0f);

        FloatBox extended = box.extend(movement);

        assertEquals(-0.5f, extended.minX(), DELTA);
        assertEquals(1.0f, extended.maxX(), DELTA);
        assertEquals(-1.0f, extended.minY(), DELTA);
        assertEquals(2.0f, extended.maxY(), DELTA);
        assertEquals(0.0f, extended.minZ(), DELTA);
        assertEquals(1.0f, extended.maxZ(), DELTA);
    }

    @Test
    void offset_translatesAllBoundsAndPreservesDimensions() {
        FloatBox box = new FloatBox(-0.3f, 0.0f, -0.3f, 0.3f, 1.8f, 0.3f);
        FloatVector translation = new FloatVector(2.0f, 3.0f, -1.0f);

        FloatBox offset = box.offset(translation);

        assertEquals(1.7f, offset.minX(), DELTA);
        assertEquals(3.0f, offset.minY(), DELTA);
        assertEquals(-1.3f, offset.minZ(), DELTA);
        assertEquals(2.3f, offset.maxX(), DELTA);
        assertEquals(4.8f, offset.maxY(), DELTA);
        assertEquals(-0.7f, offset.maxZ(), DELTA);

        float originalWidth = box.maxX() - box.minX();
        float originalHeight = box.maxY() - box.minY();
        float originalDepth = box.maxZ() - box.minZ();
        assertEquals(originalWidth, offset.maxX() - offset.minX(), DELTA);
        assertEquals(originalHeight, offset.maxY() - offset.minY(), DELTA);
        assertEquals(originalDepth, offset.maxZ() - offset.minZ(), DELTA);
    }

    @Test
    void feetPosition_returnsHorizontalCenterAtMinY() {
        FloatBox box = new FloatBox(-0.3f, 10.0f, -0.3f, 0.3f, 11.8f, 0.3f);

        FloatVector feet = box.feetPosition();

        assertEquals(0.0f, feet.x(), DELTA);
        assertEquals(10.0f, feet.y(), DELTA);
        assertEquals(0.0f, feet.z(), DELTA);
    }

    @Test
    void extend_zeroMovementOnDegenerateBox_boundsUnchanged() {
        FloatBox box = new FloatBox(5.0f, 5.0f, 5.0f, 5.0f, 5.0f, 5.0f);

        FloatBox extended = box.extend(FloatVector.ZERO);

        assertEquals(box.minX(), extended.minX(), DELTA);
        assertEquals(box.minY(), extended.minY(), DELTA);
        assertEquals(box.minZ(), extended.minZ(), DELTA);
        assertEquals(box.maxX(), extended.maxX(), DELTA);
        assertEquals(box.maxY(), extended.maxY(), DELTA);
        assertEquals(box.maxZ(), extended.maxZ(), DELTA);
    }
}
