package nay.amethyst.simulation.movement;

import java.util.ArrayList;
import java.util.List;

public final class MovementCollisionEngine {
    private static final float COLLISION_EPSILON = MovementConstants.FLT_EPSILON;
    private static final float PENETRATION_EPSILON = 1.0E-6f;
    private static final float EDGE_INSET = 0.025f;
    private static final float EDGE_STEP = 0.05f;
    private static final float SCAFFOLDING_TOP_TOLERANCE = 1.0E-6f;

    public MoveOutcome move(AuthoritativeMotionState state, MovementWorldView world,
                            float correctionThreshold) {
        FloatVector stored = state.velocity();
        FloatVector requested = stored;
        // BDS move search: requests longer than 16 blocks are scaled to 16 (divided by the length)
        float lengthSquared = (requested.y() * requested.y() + requested.x() * requested.x())
                + requested.z() * requested.z();
        if (lengthSquared > 256.0f) {
            float length = (float) Math.sqrt(lengthSquared);
            requested = new FloatVector((requested.x() / length) * 16.0f,
                    (requested.y() / length) * 16.0f, (requested.z() / length) * 16.0f);
        }
        float feetY = state.position().y();
        boolean descending = state.descendingScaffold();

        FloatBox original = state.boundingBox();
        FloatBox region = searchRegion(original, requested, MovementConstants.STEP_HEIGHT);
        List<FloatBox> collisions = solid(world.collisionBoxes(region), world, original, feetY,
                descending);

        if (state.sneaking() && state.onGround()) {
            FloatVector clipped = avoidEdge(original, requested, collisions);
            float vx = Math.abs(clipped.x()) <= MovementConstants.FLT_EPSILON ? 0.0f : stored.x();
            float vz = Math.abs(clipped.z()) <= MovementConstants.FLT_EPSILON ? 0.0f : stored.z();
            stored = new FloatVector(vx, stored.y(), vz);
            requested = clipped;
        }

        float limit = recoveryLimit(state.recoveryFlags());
        Resolution collision = resolve(original, requested, collisions, limit);
        FloatVector resolved = collision.movement();

        boolean mayStep = state.onGround()
                || requested.y() < 0.0f && requested.y() != resolved.y();
        boolean completedStep = false;

        if (mayStep && (requested.x() != resolved.x() || requested.z() != resolved.z())) {
            Resolution step = autoStep(original, requested, collisions, limit);
            boolean stepBlocked = false;
            for (FloatBox other : collisions) {
                if (step.box().intersects(other)) {
                    stepBlocked = true;
                    break;
                }
            }
            float before = resolved.z() * resolved.z() + resolved.x() * resolved.x();
            float after = step.movement().x() * step.movement().x()
                    + step.movement().z() * step.movement().z();
            if (!stepBlocked && before < after) {
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

        FloatVector endPosition = collision.box().feetPosition();
        if (state.legacySlideOffset()) {
            if (completedStep) {
                endPosition = endPosition.add(0.0f, -state.slideOffsetY(), 0.0f);
            } else {
                state.slideOffset(0.0f, 0.0f);
            }
        }
        state.position(endPosition);

        float epsilon = MovementConstants.FLT_EPSILON;
        float dy = Math.abs(requested.y() - resolved.y());
        boolean xCollision = Math.abs(requested.x() - resolved.x()) > epsilon;
        boolean yCollision = !(dy <= epsilon);
        boolean zCollision = Math.abs(requested.z() - resolved.z()) > epsilon;
        state.collisions(xCollision, yCollision, zCollision);
        state.onGround(yCollision && requested.y() < 0.0f
                || state.onGround() && !yCollision && requested.y() == 0.0f);
        // the stored velocity, reset per collided axis; every vertical collision ends with vy = 0
        state.velocity(new FloatVector(xCollision ? 0.0f : stored.x(),
                yCollision ? 0.0f : stored.y(), zCollision ? 0.0f : stored.z()));
        // the recovery flags follow the summed penetration of the resolved move
        state.recoveryFlags(updateRecovery(state.recoveryFlags(), collision.penetrated(),
                state.pushTowardsClosestSpace()));
        updateSupportingBlock(state, world, requested);
        return new MoveOutcome(requested, resolved,
                selectSupportingShape(collision.box(), collisions));
    }

    /**
     * The request actually swept, the displacement it resolved to, and the block of the shape the
     * landing reads its surface from (null when none).
     */
    public record MoveOutcome(FloatVector requested, FloatVector resolved,
                              MovementBlockPosition surface) {
    }

    /** BDS recovery limit: once penetrated twice (or pushed) and not disabled, no push-out at all. */
    static float recoveryLimit(int flags) {
        boolean suppress = (flags & 8) == 0 && (flags & 0x15) != 0;
        return suppress ? 0.0f : 1.0f;
    }

    static int updateRecovery(int flags, boolean penetrated, boolean pushTowardsClosestSpace) {
        flags = (flags & ~0x10) | (pushTowardsClosestSpace ? 0x10 : 0);
        if (penetrated) {
            flags |= (flags & 2) != 0 ? 4 : 2;
        } else {
            flags &= 0x19;
        }
        return flags;
    }

    /** BDS move search region: swept, raised, combined and sneak-edge boxes, plus the probes. */
    static FloatBox searchRegion(FloatBox box, FloatVector movement, float stepHeight) {
        float[] b = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
        float[] m = {movement.x(), movement.y(), movement.z()};
        float[] swept = expand(b, m);
        float[] raised = expand(b, new float[]{0.0f, stepHeight, 0.0f});
        float[] combined = expand(b, new float[]{m[0], stepHeight + m[1], m[2]});
        float[] inset = b.clone();
        for (int axis : new int[]{0, 2}) {
            inset[axis] += 0.025f;
            inset[axis + 3] -= 0.025f;
        }
        for (int axis = 0; axis < 3; axis++) {
            if (inset[axis] > inset[axis + 3]) {
                inset[axis] = inset[axis + 3] = (b[axis] + b[axis + 3]) * 0.5f;
            }
        }
        float[] edge = new float[6];
        for (int axis : new int[]{0, 2}) {
            edge[axis] = Math.min(Math.min(inset[axis] + m[axis], inset[axis]), swept[axis]);
            edge[axis + 3] = Math.max(Math.max(inset[axis + 3] + m[axis], inset[axis + 3]),
                    swept[axis + 3]);
        }
        float drop = stepHeight * -1.01f;
        edge[1] = Math.min(inset[1] + drop, swept[1]);
        edge[4] = Math.max(inset[4] + drop, swept[4]);
        float[] r = new float[6];
        for (int axis = 0; axis < 3; axis++) {
            r[axis] = Math.min(Math.min(Math.min(raised[axis], swept[axis]), combined[axis]), edge[axis]);
            r[axis + 3] = Math.max(Math.max(Math.max(raised[axis + 3], swept[axis + 3]),
                    combined[axis + 3]), edge[axis + 3]);
        }
        r[0] = Math.min(r[0], b[0]);
        r[2] = Math.min(r[2], b[2]);
        r[3] = Math.max(r[3], b[3]);
        r[5] = Math.max(r[5], b[5]);
        r[1] = Math.min(b[1] - (Math.abs(m[1]) + 0.2f), r[1]);
        r[4] = Math.max(b[4] + 0.08f, r[4]);
        return new FloatBox(r[0], r[1], r[2], r[3], r[4], r[5]);
    }

    private static float[] expand(float[] bounds, float[] delta) {
        float[] out = bounds.clone();
        for (int axis = 0; axis < 3; axis++) {
            if (delta[axis] < 0) out[axis] += delta[axis];
            else if (delta[axis] > 0) out[axis + 3] += delta[axis];
        }
        return out;
    }

    /** BDS supporting shape: the shape centre closest below minY - 0.2, ties by centre distance. */
    static MovementBlockPosition selectSupportingShape(FloatBox body, List<FloatBox> shapes) {
        float y = body.minY() - 0.2f;
        float x = center(body.minX(), body.maxX());
        float z = center(body.minZ(), body.maxZ());
        float bestDelta = Float.MAX_VALUE;
        FloatBox best = null;
        for (FloatBox shape : shapes) {
            float delta = y - center(shape.minY(), shape.maxY());
            if (!(delta >= 0)) continue;
            if (delta < bestDelta) {
                bestDelta = delta;
                best = shape;
            } else if (best != null && delta == bestDelta
                    && centerDistance(shape, x, y, z) < centerDistance(best, x, y, z)) {
                best = shape;
            }
        }
        if (best == null || !(best.minX() < best.maxX() && best.maxY() > best.minY()
                && best.maxZ() > best.minZ())) {
            return null;
        }
        return new MovementBlockPosition(floor(best.minX()), floor(best.minY()), floor(best.minZ()));
    }

    private static float center(float low, float high) {
        return ((high - low) * 0.5f) + low;
    }

    private static float centerDistance(FloatBox box, float x, float y, float z) {
        float dx = center(box.minX(), box.maxX()) - x;
        float dy = center(box.minY(), box.maxY()) - y;
        float dz = center(box.minZ(), box.maxZ()) - z;
        return (dx * dx + dy * dy) + dz * dz;
    }

    public static BoxCollision collide(FloatBox box, FloatVector requested, boolean wasOnGround,
                                       List<FloatBox> collisions) {
        Resolution collision = resolve(box, requested, collisions, 1.0f);
        FloatVector resolved = collision.movement();
        boolean mayStep = wasOnGround || requested.y() != resolved.y() && requested.y() < 0.0f;
        if (mayStep && (requested.x() != resolved.x() || requested.z() != resolved.z())) {
            Resolution step = autoStep(box, requested, collisions, 1.0f);
            boolean stepBlocked = false;
            for (FloatBox other : collisions) {
                if (step.box().intersects(other)) {
                    stepBlocked = true;
                    break;
                }
            }
            if (!stepBlocked && resolved.horizontalLengthSquared() < step.movement().horizontalLengthSquared()) {
                resolved = step.movement();
            }
        }
        boolean horizontal = Math.abs(requested.x() - resolved.x()) > COLLISION_EPSILON
                || Math.abs(requested.z() - resolved.z()) > COLLISION_EPSILON;
        boolean vertical = !(Math.abs(requested.y() - resolved.y()) <= COLLISION_EPSILON);
        return new BoxCollision(resolved, horizontal, vertical);
    }

    private static Resolution resolve(FloatBox original, FloatVector movement,
                                      List<FloatBox> collisions, float limit) {
        FloatBox box = original;
        float[] penetration = new float[1];

        FloatVector yMovement = new FloatVector(0.0f, movement.y(), 0.0f);
        yMovement = clipAll(collisions, box, yMovement, limit, penetration);
        box = box.offset(yMovement);

        FloatVector xMovement = new FloatVector(movement.x(), 0.0f, 0.0f);
        xMovement = clipAll(collisions, box, xMovement, limit, penetration);
        box = box.offset(xMovement);

        FloatVector zMovement = new FloatVector(0.0f, 0.0f, movement.z());
        zMovement = clipAll(collisions, box, zMovement, limit, penetration);
        box = box.offset(zMovement);

        FloatVector result = yMovement.add(xMovement).add(zMovement);
        return new Resolution(box, result, penetration[0] >= PENETRATION_EPSILON,
                FloatVector.ZERO);
    }

    private static Resolution autoStep(FloatBox original, FloatVector movement,
                                       List<FloatBox> collisions, float limit) {
        List<FloatBox> filtered = new ArrayList<>(collisions.size());
        for (FloatBox collision : collisions) {
            if (collision.minY() < original.maxY()) {
                filtered.add(collision);
            }
        }

        FloatBox box = original;
        FloatVector up = new FloatVector(0.0f, MovementConstants.STEP_HEIGHT, 0.0f);
        up = clipAll(filtered, box, up, limit, null);
        box = box.offset(up);

        FloatVector x = new FloatVector(movement.x(), 0.0f, 0.0f);
        x = clipAll(filtered, box, x, limit, null);
        box = box.offset(x);

        FloatVector z = new FloatVector(0.0f, 0.0f, movement.z());
        z = clipAll(filtered, box, z, limit, null);
        box = box.offset(z);

        FloatVector down = up.multiply(-1.0f);
        down = clipAll(filtered, box, down, limit, null);
        box = box.offset(down);
        return new Resolution(box, up.add(x).add(z).add(down), false, up);
    }

    private static FloatVector clipAll(List<FloatBox> collisions, FloatBox moving,
                                       FloatVector movement, float limit, float[] penetration) {
        FloatVector result = movement;
        for (int index = collisions.size() - 1; index >= 0; index--) {
            ClipResult clipped = clip(collisions.get(index), moving, result, limit);
            result = clipped.movement();
            if (penetration != null) {
                penetration[0] += clipped.penetration();
            }
        }
        return result;
    }

    private static ClipResult clip(FloatBox stationary, FloatBox moving,
                                   FloatVector movement, float limit) {
        if (stationary.maxX() <= stationary.minX()
                || stationary.maxY() <= stationary.minY()
                || stationary.maxZ() <= stationary.minZ()) {
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
            if (Math.abs(minimum) <= 1.0E-6f) minimum = 0.0f;
            if (Math.abs(maximum) <= 1.0E-6f) maximum = 0.0f;

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
            // past the recovery limit (or once stuck) the contact clips instead of pushing out
            if (!(minimumPenetration > limit)) {
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

    /** BDS sneak clipping against the cached shapes (body inset 0.025, drop stepHeight * 1.01). */
    private static FloatVector avoidEdge(FloatBox box, FloatVector movement, List<FloatBox> shapes) {
        float[] body = {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()};
        float[] original = body.clone();
        for (int axis : new int[]{0, 2}) {
            body[axis] = body[axis] + EDGE_INSET;
            body[axis + 3] = body[axis + 3] - EDGE_INSET;
        }
        for (int axis = 0; axis < 3; axis++) {
            if (body[axis] > body[axis + 3]) {
                float center = (original[axis] + original[axis + 3]) * 0.5f;
                body[axis] = center;
                body[axis + 3] = center;
            }
        }
        float drop = MovementConstants.STEP_HEIGHT * 1.01f;
        float x = movement.x();
        float z = movement.z();
        while (x != 0.0f && !supported(body, drop, x, 0.0f, shapes)) x = reduce(x);
        while (z != 0.0f && !supported(body, drop, 0.0f, z, shapes)) z = reduce(z);
        while ((x != 0.0f || z != 0.0f) && !supported(body, drop, x, z, shapes)) {
            x = reduce(x);
            z = reduce(z);
        }
        return new FloatVector(x, movement.y(), z);
    }

    private static boolean supported(float[] body, float drop, float x, float z, List<FloatBox> shapes) {
        float x0 = x + body[0];
        float x1 = x + body[3];
        float y0 = body[1] - drop;
        float y1 = body[4] - drop;
        float z0 = z + body[2];
        float z1 = z + body[5];
        for (FloatBox box : shapes) {
            if (x1 > box.minX() && x0 < box.maxX() && y1 > box.minY() && y0 < box.maxY()
                    && z1 > box.minZ() && z0 < box.maxZ()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Scaffolding as BDS collides it: its own shapes are replaced by one full cube, present only
     * when the block is stable (stability != 7), the actor is not falling through, its feet are at
     * or above the block top (1e-6 tolerance) and it overlaps the cell horizontally.
     */
    public static List<FloatBox> solid(List<FloatBox> boxes, MovementWorldView world, float feetY,
                                       boolean descending) {
        return solid(boxes, world, null, feetY, descending);
    }

    public static List<FloatBox> solid(List<FloatBox> boxes, MovementWorldView world, FloatBox actor,
                                       float feetY, boolean descending) {
        List<FloatBox> kept = null;
        java.util.Set<Long> added = null;
        for (int index = 0; index < boxes.size(); index++) {
            FloatBox box = boxes.get(index);
            int x = floor(box.minX());
            int y = floor(box.minY());
            int z = floor(box.minZ());
            MovementBlockView block = world.block(x, y, z);
            boolean scaffolding = block.named("scaffolding");
            if (!scaffolding) {
                if (kept != null) kept.add(box);
                continue;
            }
            if (kept == null) {
                kept = new ArrayList<>(boxes.subList(0, index));
                added = new java.util.HashSet<>();
            }
            boolean supports = block.scaffoldingStability() != 7 && !descending
                    && feetY >= (float) (y + 1) - SCAFFOLDING_TOP_TOLERANCE
                    && (actor == null || actor.maxX() > x && actor.minX() < x + 1
                    && actor.maxZ() > z && actor.minZ() < z + 1);
            long key = ((long) x << 42) ^ ((long) (y & 0x1FFFFF) << 21) ^ (z & 0x1FFFFF);
            if (supports && added.add(key)) {
                kept.add(new FloatBox(x, y, z, x + 1.0f, y + 1.0f, z + 1.0f));
            }
        }
        return kept == null ? boxes : kept;
    }

    private static float reduce(float value) {
        if (value >= 0.0f) {
            float next = value - EDGE_STEP;
            return next <= value ? Math.max(next, 0.0f) : value;
        }
        float next = value + EDGE_STEP;
        return next <= 0.0f ? Math.max(next, value) : 0.0f;
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

    public record BoxCollision(FloatVector movement, boolean horizontalCollision,
                               boolean verticalCollision) {
    }

    private record Resolution(FloatBox box, FloatVector movement,
                              boolean penetrated, FloatVector upMovement) {
    }

    private record ClipResult(FloatVector movement, int axis, float penetration) {
    }
}
