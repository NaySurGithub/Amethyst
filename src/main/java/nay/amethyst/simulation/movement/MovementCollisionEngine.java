package nay.amethyst.simulation.movement;

import java.util.ArrayList;
import java.util.List;

public final class MovementCollisionEngine {
    private static final float COLLISION_EPSILON = 1.0E-5f;
    private static final float PENETRATION_EPSILON_SQUARED = 1.0E-11f;
    private static final float EDGE_INSET = 0.025f;
    private static final float EDGE_STEP = 0.05f;

    public void move(AuthoritativeMotionState state, MovementWorldView world,
                     float correctionThreshold) {
        FloatVector requested = state.velocity();
        if (state.sneaking() && state.onGround() && requested.y() <= 0.0f) {
            requested = avoidEdge(state.boundingBox(), requested, world);
            state.velocity(requested);
        }

        FloatBox original = state.boundingBox();
        List<FloatBox> collisions = world.collisionBoxes(original.extend(requested));
        Resolution collision = resolve(original, requested, collisions, state.stuckInCollider());
        FloatVector resolved = collision.movement();
        boolean penetrated = collision.penetrated();

        state.stuckInCollider(state.penetratedLastFrame() && penetrated);
        state.penetratedLastFrame(penetrated);

        boolean xCollision = requested.x() != resolved.x();
        boolean yCollision = requested.y() != resolved.y();
        boolean zCollision = requested.z() != resolved.z();
        boolean mayStep = state.onGround() || yCollision && requested.y() < 0.0f;
        boolean completedStep = false;

        if (mayStep && (xCollision || zCollision)) {
            Resolution step = autoStep(original, requested, collisions, state.stuckInCollider());
            boolean stepBlocked = !world.collisionBoxes(step.box()).isEmpty();
            if (!stepBlocked && resolved.horizontalLengthSquared() < step.movement().horizontalLengthSquared()) {
                float normalDistance = collision.box().feetPosition().subtract(state.client().position()).length();
                float stepDistance = step.box().feetPosition().subtract(state.client().position()).length();
                if (normalDistance > correctionThreshold || stepDistance <= normalDistance) {
                    collision = step;
                    resolved = step.movement();
                    completedStep = true;
                    if (state.legacySlideOffset()) {
                        state.slideOffset(state.slideOffsetX() * MovementConstants.SLIDE_OFFSET_MULTIPLIER,
                                state.slideOffsetY() * MovementConstants.SLIDE_OFFSET_MULTIPLIER
                                        + step.upMovement().y());
                    }
                }
            }
        }

        FloatVector endPosition = collision.box().feetPosition();
        if (state.legacySlideOffset()) {
            if (completedStep) {
                endPosition = endPosition.add(0.0f, -state.slideOffsetY(), 0.0f);
            } else {
                state.slideOffset(0.0f, 0.0f);
            }
        }
        state.position(endPosition);

        xCollision = Math.abs(requested.x() - resolved.x()) >= COLLISION_EPSILON;
        yCollision = Math.abs(requested.y() - resolved.y()) >= COLLISION_EPSILON;
        zCollision = Math.abs(requested.z() - resolved.z()) >= COLLISION_EPSILON;
        state.collisions(xCollision, yCollision, zCollision);
        state.onGround(yCollision && requested.y() < 0.0f
                || state.onGround() && !yCollision && Math.abs(requested.y()) <= COLLISION_EPSILON);
        state.velocity(resolved);
        updateSupportingBlock(state, world, requested);
    }

    private static Resolution resolve(FloatBox original, FloatVector movement,
                                      List<FloatBox> collisions, boolean oneWay) {
        FloatBox box = original;
        float[] penetration = new float[3];

        FloatVector yMovement = new FloatVector(0.0f, movement.y(), 0.0f);
        yMovement = clipAll(collisions, box, yMovement, oneWay, penetration);
        box = box.offset(yMovement);

        FloatVector xMovement = new FloatVector(movement.x(), 0.0f, 0.0f);
        xMovement = clipAll(collisions, box, xMovement, oneWay, penetration);
        box = box.offset(xMovement);

        FloatVector zMovement = new FloatVector(0.0f, 0.0f, movement.z());
        zMovement = clipAll(collisions, box, zMovement, oneWay, penetration);
        box = box.offset(zMovement);

        FloatVector result = yMovement.add(xMovement).add(zMovement);
        float penetrationSquared = penetration[0] * penetration[0]
                + penetration[1] * penetration[1] + penetration[2] * penetration[2];
        return new Resolution(box, result, penetrationSquared >= PENETRATION_EPSILON_SQUARED,
                FloatVector.ZERO);
    }

    private static Resolution autoStep(FloatBox original, FloatVector movement,
                                       List<FloatBox> collisions, boolean oneWay) {
        List<FloatBox> filtered = new ArrayList<>(collisions.size());
        for (FloatBox collision : collisions) {
            if (collision.minY() < original.maxY()) {
                filtered.add(collision);
            }
        }

        FloatBox box = original;
        FloatVector up = new FloatVector(0.0f, MovementConstants.STEP_HEIGHT, 0.0f);
        up = clipAll(filtered, box, up, oneWay, null);
        box = box.offset(up);

        FloatVector x = new FloatVector(movement.x(), 0.0f, 0.0f);
        x = clipAll(filtered, box, x, oneWay, null);
        box = box.offset(x);

        FloatVector z = new FloatVector(0.0f, 0.0f, movement.z());
        z = clipAll(filtered, box, z, oneWay, null);
        box = box.offset(z);

        FloatVector down = up.multiply(-1.0f);
        down = clipAll(filtered, box, down, oneWay, null);
        box = box.offset(down);
        return new Resolution(box, up.add(x).add(z).add(down), false, up);
    }

    private static FloatVector clipAll(List<FloatBox> collisions, FloatBox moving,
                                       FloatVector movement, boolean oneWay, float[] penetration) {
        FloatVector result = movement;
        for (int index = collisions.size() - 1; index >= 0; index--) {
            ClipResult clipped = clip(collisions.get(index), moving, result, oneWay);
            result = clipped.movement();
            if (penetration != null
                    && penetration[clipped.axis()] < clipped.penetration()) {
                penetration[clipped.axis()] = clipped.penetration();
            }
        }
        return result;
    }

    private static ClipResult clip(FloatBox stationary, FloatBox moving,
                                   FloatVector movement, boolean oneWay) {
        if (stationary.minX() == stationary.maxX()
                && stationary.minY() == stationary.maxY()
                && stationary.minZ() == stationary.maxZ()) {
            return new ClipResult(movement, 0, 0.0f);
        }

        float[] stationaryMinimum = {stationary.minX(), stationary.minY(), stationary.minZ()};
        float[] stationaryMaximum = {stationary.maxX(), stationary.maxY(), stationary.maxZ()};
        float[] movingMinimum = {moving.minX(), moving.minY(), moving.minZ()};
        float[] movingMaximum = {moving.maxX(), moving.maxY(), moving.maxZ()};
        float[] requested = {movement.x(), movement.y(), movement.z()};

        float[] penetration = new float[3];
        float[] signedPenetration = new float[3];
        float[] normal = new float[3];
        int separatingAxes = 0;
        int separatingAxis = 0;
        float minimumPenetration = Float.MAX_VALUE - 1.0f;

        for (int axis = 0; axis < 3; axis++) {
            float minimum = movingMaximum[axis] - stationaryMinimum[axis];
            float maximum = stationaryMaximum[axis] - movingMinimum[axis];
            if (Math.abs(minimum) <= 1.0E-7f) minimum = 0.0f;
            if (Math.abs(maximum) <= 1.0E-7f) maximum = 0.0f;

            float positiveMinimum = Math.max(0.0f, minimum);
            float positiveMaximum = Math.max(0.0f, maximum);
            if (positiveMinimum == 0.0f) {
                signedPenetration[axis] = minimum;
                normal[axis] = -1.0f;
                separatingAxes++;
                separatingAxis = axis;
            } else if (positiveMaximum == 0.0f) {
                signedPenetration[axis] = maximum;
                normal[axis] = 1.0f;
                separatingAxes++;
                separatingAxis = axis;
            } else if (positiveMinimum < positiveMaximum) {
                penetration[axis] = positiveMinimum;
                signedPenetration[axis] = positiveMinimum;
                normal[axis] = -1.0f;
            } else {
                penetration[axis] = positiveMaximum;
                signedPenetration[axis] = positiveMaximum;
                normal[axis] = 1.0f;
            }

            if (separatingAxes > 1) {
                return new ClipResult(movement, 0, 0.0f);
            }
            minimumPenetration = Math.min(minimumPenetration, penetration[axis]);
        }

        if (separatingAxes == 0) {
            int axis = 0;
            if (penetration[1] < penetration[axis]) axis = 1;
            if (penetration[2] < penetration[axis]) axis = 2;
            if (!oneWay) {
                float desired = penetration[axis] * normal[axis];
                requested[axis] = desired > 0.0f
                        ? Math.max(desired, requested[axis])
                        : Math.min(desired, requested[axis]);
            }
            return new ClipResult(vector(requested), axis, minimumPenetration);
        }

        float sweptPenetration = signedPenetration[separatingAxis]
                - normal[separatingAxis] * requested[separatingAxis];
        if (sweptPenetration <= 0.0f) {
            return new ClipResult(movement, 0, 0.0f);
        }
        requested[separatingAxis] = signedPenetration[separatingAxis]
                * normal[separatingAxis];
        return new ClipResult(vector(requested), 0, 0.0f);
    }

    private static FloatVector avoidEdge(FloatBox box, FloatVector movement,
                                         MovementWorldView world) {
        FloatBox support = new FloatBox(box.minX() + EDGE_INSET, box.minY(), box.minZ() + EDGE_INSET,
                box.maxX() - EDGE_INSET, box.maxY(), box.maxZ() - EDGE_INSET);
        float x = movement.x();
        float z = movement.z();
        while (x != 0.0f && !supported(support, x, 0.0f, world)) x = reduce(x);
        while (z != 0.0f && !supported(support, 0.0f, z, world)) z = reduce(z);
        while (x != 0.0f && z != 0.0f && !supported(support, x, z, world)) {
            x = reduce(x);
            z = reduce(z);
        }
        return new FloatVector(x, movement.y(), z);
    }

    private static boolean supported(FloatBox box, float x, float z,
                                     MovementWorldView world) {
        FloatBox moved = box.offset(new FloatVector(x,
                -MovementConstants.STEP_HEIGHT * 1.01f, z));
        return !world.collisionBoxes(moved).isEmpty();
    }

    private static float reduce(float value) {
        if (value < EDGE_STEP && value >= -EDGE_STEP) return 0.0f;
        return value > 0.0f ? value - EDGE_STEP : value + EDGE_STEP;
    }

    private static void updateSupportingBlock(AuthoritativeMotionState state,
                                              MovementWorldView world,
                                              FloatVector requested) {
        if (!state.onGround()) {
            state.clearSupportingBlock();
            return;
        }
        FloatBox probe = state.boundingBox().extend(new FloatVector(0.0f, -1.0E-3f, 0.0f));
        MovementBlockPosition supporting = world.supportingBlock(probe, state.position());
        if (supporting == null) {
            probe = probe.offset(new FloatVector(-requested.x(), 0.0f, -requested.z()));
            supporting = world.supportingBlock(probe, state.position());
        }
        if (supporting == null) {
            state.clearSupportingBlock();
            return;
        }
        state.supportingBlock(supporting.x(), supporting.y(), supporting.z());
    }

    private static FloatVector vector(float[] values) {
        return new FloatVector(values[0], values[1], values[2]);
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    private record Resolution(FloatBox box, FloatVector movement,
                              boolean penetrated, FloatVector upMovement) {
    }

    private record ClipResult(FloatVector movement, int axis, float penetration) {
    }
}
