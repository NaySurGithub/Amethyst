package nay.amethyst.prediction.movement;

import nay.amethyst.history.model.Aabb;
import nay.amethyst.history.model.WorldFrame;
import nay.amethyst.prediction.common.Vec3;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.block.BlockFenceGate;
import org.powernukkitx.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

public final class CollisionResolver {
    private static final float COLLISION_EPSILON = 1.0E-5f;
    private static final float HORIZONTAL_BOX_INSET = 1.0E-4f;
    private static final float EDGE_BOUNDARY = 0.025f;
    private static final float EDGE_STEP = 0.05f;

    private CollisionResolver() {
    }

    static CollisionResult move(Player player, Vec3 clientPosition, Vec3 requested, boolean wasOnGround) {
        return move(player, clientPosition, requested, wasOnGround, null, false, false, null);
    }

    static CollisionResult move(Player player, Vec3 clientPosition, Vec3 requested, boolean wasOnGround,
                                WorldFrame frame) {
        return move(player, clientPosition, requested, wasOnGround, frame, false, false, null);
    }

    static CollisionResult move(Player player, Vec3 clientPosition, Vec3 requested, boolean wasOnGround,
                                WorldFrame frame, boolean sneaking) {
        return move(player, clientPosition, requested, wasOnGround, frame, sneaking, false, null);
    }

    static CollisionResult move(Player player, Vec3 clientPosition, Vec3 requested, boolean wasOnGround,
                                WorldFrame frame, boolean sneaking, boolean oneWayCollision) {
        return move(player, clientPosition, requested, wasOnGround, frame, sneaking, oneWayCollision, null);
    }

    static CollisionResult move(Player player, Vec3 clientPosition, Vec3 requested, boolean wasOnGround,
                                WorldFrame frame, boolean sneaking, boolean oneWayCollision,
                                Vec3 clientTarget) {
        Aabb original = floatBox(playerBox(player, clientPosition, frame));
        Vec3 movement = floatVector(requested);
        if (sneaking && wasOnGround && movement.y() <= 0.0f) {
            movement = avoidEdge(player, original, movement, frame);
        }

        List<Aabb> collisions = collisionBoxes(player, original.stretch(movement), frame);
        Resolution collision = resolve(original, movement, collisions, oneWayCollision);
        Vec3 resolved = collision.movement();
        boolean xCollision = different(movement.x(), resolved.x());
        boolean yCollision = different(movement.y(), resolved.y());
        boolean zCollision = different(movement.z(), resolved.z());
        boolean mayStep = wasOnGround || yCollision && movement.y() < 0.0f;

        if (mayStep && (xCollision || zCollision)) {
            Resolution step = calculateAutoStep(original, movement, collisions, oneWayCollision);
            boolean stepIntersects = collisionBoxes(player, step.box(), frame).stream()
                    .anyMatch(step.box()::intersects);
            if (!stepIntersects && horizontalSquared(resolved) < horizontalSquared(step.movement())) {
                Vec3 collisionPosition = boxPosition(collision.box(), player, frame);
                Vec3 stepPosition = boxPosition(step.box(), player, frame);
                double collisionDistance = clientTarget == null
                        ? Double.POSITIVE_INFINITY : collisionPosition.distance(clientTarget);
                double stepDistance = clientTarget == null
                        ? 0.0 : stepPosition.distance(clientTarget);
                if (collisionDistance > BedrockMath.CORRECTION_THRESHOLD
                        || stepDistance <= collisionDistance) {
                    collision = new Resolution(step.box(), step.movement(), collision.penetrated());
                    resolved = step.movement();
                    xCollision = different(movement.x(), resolved.x());
                    yCollision = different(movement.y(), resolved.y());
                    zCollision = different(movement.z(), resolved.z());
                }
            }
        }

        xCollision = collided(movement.x(), resolved.x());
        yCollision = collided(movement.y(), resolved.y());
        zCollision = collided(movement.z(), resolved.z());
        boolean onGround = yCollision && movement.y() < 0.0f
                || wasOnGround && !yCollision && Math.abs((float) movement.y()) <= COLLISION_EPSILON;
        return new CollisionResult(resolved, xCollision, yCollision, zCollision,
                onGround, collision.penetrated());
    }

    public static CollisionResult resolveWithBoxes(Aabb box, Vec3 requested, boolean wasOnGround,
                                                   List<Aabb> collisions) {
        Vec3 movement = floatVector(requested);
        Resolution collision = resolve(box, movement, collisions, false);
        Vec3 resolved = collision.movement();
        boolean xCollision = different(movement.x(), resolved.x());
        boolean yCollision = different(movement.y(), resolved.y());
        boolean zCollision = different(movement.z(), resolved.z());
        boolean mayStep = wasOnGround || yCollision && movement.y() < 0.0f;

        if (mayStep && (xCollision || zCollision)) {
            Resolution step = calculateAutoStep(box, movement, collisions, false);
            boolean stepIntersects = collisions.stream().anyMatch(step.box()::intersects);
            if (!stepIntersects && horizontalSquared(resolved) < horizontalSquared(step.movement())) {
                collision = new Resolution(step.box(), step.movement(), collision.penetrated());
                resolved = step.movement();
                xCollision = different(movement.x(), resolved.x());
                yCollision = different(movement.y(), resolved.y());
                zCollision = different(movement.z(), resolved.z());
            }
        }

        xCollision = collided(movement.x(), resolved.x());
        yCollision = collided(movement.y(), resolved.y());
        zCollision = collided(movement.z(), resolved.z());
        boolean onGround = yCollision && movement.y() < 0.0f
                || wasOnGround && !yCollision && Math.abs((float) movement.y()) <= COLLISION_EPSILON;
        return new CollisionResult(resolved, xCollision, yCollision, zCollision,
                onGround, collision.penetrated());
    }

    private static Resolution resolve(Aabb original, Vec3 movement, List<Aabb> collisions,
                                      boolean oneWayCollision) {
        Aabb box = floatBox(original);
        float[] penetration = new float[3];

        Vec3 yVelocity = new Vec3(0.0f, movement.y(), 0.0f);
        yVelocity = clipAll(collisions, box, yVelocity, oneWayCollision, penetration);
        box = translate(box, yVelocity);

        Vec3 xVelocity = new Vec3(movement.x(), 0.0f, 0.0f);
        xVelocity = clipAll(collisions, box, xVelocity, oneWayCollision, penetration);
        box = translate(box, xVelocity);

        Vec3 zVelocity = new Vec3(0.0f, 0.0f, movement.z());
        zVelocity = clipAll(collisions, box, zVelocity, oneWayCollision, penetration);
        box = translate(box, zVelocity);

        Vec3 resolved = floatVector(yVelocity.add(xVelocity).add(zVelocity));
        float penetrationSquared = penetration[0] * penetration[0]
                + penetration[1] * penetration[1]
                + penetration[2] * penetration[2];
        return new Resolution(box, resolved, penetrationSquared >= 1.0E-11f);
    }

    private static Resolution calculateAutoStep(Aabb original, Vec3 movement, List<Aabb> collisions,
                                                boolean oneWayCollision) {
        List<Aabb> stepCollisions = new ArrayList<>(collisions.size());
        for (Aabb collision : collisions) {
            if (collision.minY() < original.maxY()) {
                stepCollisions.add(collision);
            }
        }

        Aabb box = floatBox(original);
        Vec3 upVelocity = new Vec3(0.0f, BedrockMath.STEP_HEIGHT, 0.0f);
        upVelocity = clipAll(stepCollisions, box, upVelocity, oneWayCollision, null);
        box = translate(box, upVelocity);

        Vec3 xVelocity = new Vec3(movement.x(), 0.0f, 0.0f);
        xVelocity = clipAll(stepCollisions, box, xVelocity, oneWayCollision, null);
        box = translate(box, xVelocity);

        Vec3 zVelocity = new Vec3(0.0f, 0.0f, movement.z());
        zVelocity = clipAll(stepCollisions, box, zVelocity, oneWayCollision, null);
        box = translate(box, zVelocity);

        Vec3 downVelocity = new Vec3(-(float) upVelocity.x(), -(float) upVelocity.y(),
                -(float) upVelocity.z());
        downVelocity = clipAll(stepCollisions, box, downVelocity, oneWayCollision, null);
        box = translate(box, downVelocity);

        Vec3 resolved = floatVector(upVelocity.add(xVelocity).add(zVelocity).add(downVelocity));
        return new Resolution(box, resolved, false);
    }

    private static Vec3 clipAll(List<Aabb> collisions, Aabb moving, Vec3 velocity,
                                boolean oneWayCollision, float[] penetration) {
        Vec3 result = floatVector(velocity);
        for (int index = collisions.size() - 1; index >= 0; index--) {
            ClipResult clipped = clipCollide(collisions.get(index), moving, result, oneWayCollision);
            result = clipped.movement();
            if (penetration != null && penetration[clipped.depenetratingAxis()] < clipped.penetration()) {
                penetration[clipped.depenetratingAxis()] = clipped.penetration();
            }
        }
        return result;
    }

    private static ClipResult clipCollide(Aabb stationary, Aabb moving, Vec3 velocity,
                                          boolean oneWayCollision) {
        float[] stationaryMin = values(stationary.minX(), stationary.minY(), stationary.minZ());
        float[] stationaryMax = values(stationary.maxX(), stationary.maxY(), stationary.maxZ());
        float[] movingMin = values(moving.minX(), moving.minY(), moving.minZ());
        float[] movingMax = values(moving.maxX(), moving.maxY(), moving.maxZ());
        float[] requested = values(velocity.x(), velocity.y(), velocity.z());

        if (stationaryMin[0] == stationaryMax[0]
                && stationaryMin[1] == stationaryMax[1]
                && stationaryMin[2] == stationaryMax[2]) {
            return new ClipResult(floatVector(velocity), 0, 0.0f);
        }

        float[] axisPenetrations = new float[3];
        float[] signedPenetrations = new float[3];
        float[] normalDirections = new float[3];
        int separatingAxes = 0;
        int separatingAxis = 0;
        float resultPenetration = Float.MAX_VALUE - 1.0f;

        for (int axis = 0; axis < 3; axis++) {
            float minPenetration = movingMax[axis] - stationaryMin[axis];
            float maxPenetration = stationaryMax[axis] - movingMin[axis];
            if (Math.abs(minPenetration) <= 1.0E-7f) {
                minPenetration = 0.0f;
            }
            if (Math.abs(maxPenetration) <= 1.0E-7f) {
                maxPenetration = 0.0f;
            }

            float minPositive = Math.max(0.0f, minPenetration);
            float maxPositive = Math.max(0.0f, maxPenetration);
            if (minPositive == 0.0f) {
                signedPenetrations[axis] = minPenetration;
                normalDirections[axis] = -1.0f;
                separatingAxes++;
                separatingAxis = axis;
            } else if (maxPositive == 0.0f) {
                signedPenetrations[axis] = maxPenetration;
                normalDirections[axis] = 1.0f;
                separatingAxes++;
                separatingAxis = axis;
            } else if (minPositive < maxPositive) {
                axisPenetrations[axis] = minPositive;
                signedPenetrations[axis] = minPositive;
                normalDirections[axis] = -1.0f;
            } else {
                axisPenetrations[axis] = maxPositive;
                signedPenetrations[axis] = maxPositive;
                normalDirections[axis] = 1.0f;
            }

            if (separatingAxes > 1) {
                return new ClipResult(floatVector(velocity), 0, 0.0f);
            }
            resultPenetration = Math.min(resultPenetration, axisPenetrations[axis]);
        }

        if (separatingAxes == 0) {
            int bestAxis = 0;
            for (int axis = 1; axis < 3; axis++) {
                if (axisPenetrations[axis] < axisPenetrations[bestAxis]) {
                    bestAxis = axis;
                }
            }
            if (!oneWayCollision) {
                float desired = axisPenetrations[bestAxis] * normalDirections[bestAxis];
                requested[bestAxis] = desired > 0.0f
                        ? Math.max(desired, requested[bestAxis])
                        : Math.min(desired, requested[bestAxis]);
            }
            return new ClipResult(vector(requested), bestAxis, resultPenetration);
        }

        float sweptPenetration = signedPenetrations[separatingAxis]
                - normalDirections[separatingAxis] * requested[separatingAxis];
        if (sweptPenetration <= 0.0f) {
            return new ClipResult(floatVector(velocity), 0, 0.0f);
        }
        requested[separatingAxis] = signedPenetrations[separatingAxis]
                * normalDirections[separatingAxis];
        return new ClipResult(vector(requested), 0, 0.0f);
    }

    private static Vec3 avoidEdge(Player player, Aabb box, Vec3 movement, WorldFrame frame) {
        Aabb supportBox = new Aabb(box.minX() + EDGE_BOUNDARY, box.minY(), box.minZ() + EDGE_BOUNDARY,
                box.maxX() - EDGE_BOUNDARY, box.maxY(), box.maxZ() - EDGE_BOUNDARY);
        float x = (float) movement.x();
        float z = (float) movement.z();

        while (x != 0.0f && !supported(player, supportBox, x, 0.0f, frame)) {
            x = reduceTowardsZero(x);
        }
        while (z != 0.0f && !supported(player, supportBox, 0.0f, z, frame)) {
            z = reduceTowardsZero(z);
        }
        while (x != 0.0f && z != 0.0f && !supported(player, supportBox, x, z, frame)) {
            x = reduceTowardsZero(x);
            z = reduceTowardsZero(z);
        }
        return new Vec3(x, (float) movement.y(), z);
    }

    private static boolean supported(Player player, Aabb box, float x, float z, WorldFrame frame) {
        Aabb moved = box.offset(x, -BedrockMath.STEP_HEIGHT * 1.01f, z);
        return collisionBoxes(player, moved, frame).stream().anyMatch(moved::intersects);
    }

    private static float reduceTowardsZero(float value) {
        if (value < EDGE_STEP && value >= -EDGE_STEP) {
            return 0.0f;
        }
        return value > 0.0f ? value - EDGE_STEP : value + EDGE_STEP;
    }

    private static List<Aabb> collisionBoxes(Player player, Aabb area, WorldFrame frame) {
        area = floatBox(area);
        if (frame != null && frame.levelName().equals(player.getLevel().getName()) && frame.covers(area)) {
            return frame.collisionBoxes(area);
        }
        List<Aabb> boxes = new ArrayList<>();
        Aabb grown = floatBox(area.expand(1.0f));
        int minX = floor(grown.minX());
        int minY = floor(grown.minY());
        int minZ = floor(grown.minZ());
        int maxX = ceil(grown.maxX());
        int maxY = ceil(grown.maxY());
        int maxZ = ceil(grown.maxZ());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    addBlockCollisions(boxes, player.getLevel().getBlock(x, y, z, 0), area);
                    addBlockCollisions(boxes, player.getLevel().getBlock(x, y, z, 1), area);
                }
            }
        }
        return boxes;
    }

    private static void addBlockCollisions(List<Aabb> boxes, Block block, Aabb area) {
        if (block == null || block.isAir()
                || block instanceof BlockFenceGate gate && gate.isOpen()) {
            return;
        }
        AxisAlignedBB[] blockBoxes = block.getCollisionBoxes();
        if (blockBoxes == null) {
            return;
        }
        for (AxisAlignedBB box : blockBoxes) {
            if (box == null) {
                continue;
            }
            Aabb converted = floatBox(Aabb.from(box));
            if (converted.intersects(area)) {
                boxes.add(converted);
            }
        }
    }

    private static Aabb playerBox(Player player, Vec3 clientPosition, WorldFrame frame) {
        float width = (float) (frame == null ? player.getWidth() : frame.physics().width());
        float height = (float) (frame == null ? player.getHeight() : frame.physics().height());
        float scale = (float) (frame == null ? player.getScale() : frame.physics().scale());
        float baseOffset = (float) (frame == null ? player.getBaseOffset() : frame.physics().baseOffset());
        float x = (float) clientPosition.x();
        float y = (float) clientPosition.y();
        float z = (float) clientPosition.z();
        float halfWidth = width * scale / 2.0f;
        float feet = y - baseOffset;
        return new Aabb(x - halfWidth + HORIZONTAL_BOX_INSET, feet,
                z - halfWidth + HORIZONTAL_BOX_INSET,
                x + halfWidth - HORIZONTAL_BOX_INSET, feet + height * scale,
                z + halfWidth - HORIZONTAL_BOX_INSET);
    }

    private static Vec3 boxPosition(Aabb box, Player player, WorldFrame frame) {
        float baseOffset = (float) (frame == null ? player.getBaseOffset() : frame.physics().baseOffset());
        return new Vec3(((float) box.minX() + (float) box.maxX()) * 0.5f,
                (float) box.minY() + baseOffset,
                ((float) box.minZ() + (float) box.maxZ()) * 0.5f);
    }

    private static boolean collided(double requested, double resolved) {
        return Math.abs((float) requested - (float) resolved) >= COLLISION_EPSILON;
    }

    private static boolean different(double requested, double resolved) {
        return Float.compare((float) requested, (float) resolved) != 0;
    }

    private static double horizontalSquared(Vec3 vector) {
        float x = (float) vector.x();
        float z = (float) vector.z();
        return x * x + z * z;
    }

    private static float[] values(double x, double y, double z) {
        return new float[]{(float) x, (float) y, (float) z};
    }

    private static Vec3 vector(float[] values) {
        return new Vec3(values[0], values[1], values[2]);
    }

    private static Vec3 floatVector(Vec3 vector) {
        return new Vec3((float) vector.x(), (float) vector.y(), (float) vector.z());
    }

    private static Aabb floatBox(Aabb box) {
        return new Aabb((float) box.minX(), (float) box.minY(), (float) box.minZ(),
                (float) box.maxX(), (float) box.maxY(), (float) box.maxZ());
    }

    private static Aabb translate(Aabb box, Vec3 movement) {
        return floatBox(box.offset((float) movement.x(), (float) movement.y(),
                (float) movement.z()));
    }

    private static int floor(double value) {
        return (int) Math.floor((float) value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil((float) value);
    }

    public record CollisionResult(Vec3 movement, boolean xCollision,
                                  boolean verticalCollision, boolean zCollision,
                                  boolean onGround, boolean penetrated) {
        public boolean horizontalCollision() {
            return xCollision || zCollision;
        }
    }

    private record Resolution(Aabb box, Vec3 movement, boolean penetrated) {
    }

    private record ClipResult(Vec3 movement, int depenetratingAxis, float penetration) {
    }
}
