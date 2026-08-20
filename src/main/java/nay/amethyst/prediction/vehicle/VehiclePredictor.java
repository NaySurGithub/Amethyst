package nay.amethyst.prediction.vehicle;

import nay.amethyst.data.player.PlayerData;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.history.model.Aabb;
import nay.amethyst.prediction.common.Vec3;
import nay.amethyst.prediction.movement.CollisionResolver;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.powernukkitx.Player;
import org.powernukkitx.block.Block;
import org.powernukkitx.entity.Entity;
import org.powernukkitx.entity.item.EntityBoat;
import org.powernukkitx.entity.item.EntityMinecartAbstract;
import org.powernukkitx.math.AxisAlignedBB;
import org.powernukkitx.math.SimpleAxisAlignedBB;

import java.util.ArrayList;
import java.util.List;

public final class VehiclePredictor {
    public VehiclePredictionResult predict(Player player, PlayerData data, PlayerAuthInputPacket packet,
                                           long tickDelta) {
        Entity vehicle = player.getRiding();
        if (vehicle == null) throw new IllegalStateException("Player is not riding");
        int ticks = (int) Math.max(1, Math.min(tickDelta, 5));

        if (vehicle instanceof EntityMinecartAbstract || !vehicle.hasWASDControls()
                && !(vehicle instanceof EntityBoat)) {
            return passiveVehicle(player, vehicle, packet, ticks);
        }

        Vec3 start = new Vec3(vehicle.x, vehicle.y, vehicle.z);
        Vec3 target = vehicleTarget(vehicle, packet.getPosition());
        Vec3 requested = target.add(-start.x(), -start.y(), -start.z());
        Vec3 current = data.predictedVehicleVelocity == null ? Vec3.ZERO : data.predictedVehicleVelocity;
        Vec3 server = new Vec3(vehicle.motionX, vehicle.motionY, vehicle.motionZ);
        Vector3f delta = packet.getPosDelta();
        Vec3 reported = delta == null ? Vec3.ZERO : new Vec3(delta.getX(), delta.getY(), delta.getZ());
        if (reported.lengthSquared() > 9) reported = Vec3.ZERO;
        VehicleSimulation simulation = bestSimulation(vehicle, packet, requested, current, server, reported, ticks);

        CollisionResolver.CollisionResult collision = collide(vehicle, requested);
        double collisionError = requested.distance(collision.movement());
        double physicsError = requested.distance(simulation.movement());
        double limitError = movementLimitError(vehicle, requested, ticks);
        double entityAllowance = nearbyEntityAllowance(vehicle, requested);
        double modelTolerance = (vehicle instanceof EntityBoat ? 0.28 : 0.32) * ticks + entityAllowance;
        double offset = Math.max(limitError, Math.max(Math.max(0, physicsError - modelTolerance),
                collisionError > 0.08 ? collisionError : 0));
        return new VehiclePredictionResult(vehicle instanceof EntityBoat ? "boat" : "mount", offset,
                requested, simulation.velocity(), collision.horizontalCollision(), collision.verticalCollision());
    }

    private static VehiclePredictionResult passiveVehicle(Player player, Entity vehicle,
                                                            PlayerAuthInputPacket packet, int ticks) {
        Vector3f position = packet.getPosition();
        Vec3 target = new Vec3(position.getX(), position.getY(), position.getZ());
        Vec3 serverSeat = new Vec3(player.x, player.y + player.getBaseOffset(), player.z);
        Vec3 movement = new Vec3(vehicle.motionX * ticks, vehicle.motionY * ticks, vehicle.motionZ * ticks);
        double latencyTicks = Math.min(2, Math.max(0, NetworkCheckSupport.ping(player) / 50.0));
        double tolerance = (vehicle instanceof EntityMinecartAbstract ? 0.45 : 0.6)
                + Math.hypot(vehicle.motionX, vehicle.motionZ) * latencyTicks;
        double offset = Math.max(0, target.distance(serverSeat) - tolerance);
        return new VehiclePredictionResult(vehicle instanceof EntityMinecartAbstract ? "minecart" : "passive-mount",
                offset, movement, new Vec3(vehicle.motionX, vehicle.motionY, vehicle.motionZ), false, false);
    }

    private static VehicleSimulation bestSimulation(Entity vehicle, PlayerAuthInputPacket packet, Vec3 requested,
                                                     Vec3 predicted, Vec3 server, Vec3 reported, int ticks) {
        List<Vec3> starts = List.of(predicted, server, reported, Vec3.ZERO);
        VehicleSimulation best = null;
        for (Vec3 start : starts) {
            VehicleSimulation candidate = vehicle instanceof EntityBoat
                    ? predictBoat(vehicle, packet, start, ticks)
                    : predictMount(vehicle, packet, start, ticks);
            if (best == null || requested.distance(candidate.movement()) < requested.distance(best.movement())) {
                best = candidate;
            }
        }
        return best;
    }

    private static VehicleSimulation predictBoat(Entity vehicle, PlayerAuthInputPacket packet, Vec3 start,
                                                  int ticks) {
        Vec3 velocity = start;
        Vec3 movement = Vec3.ZERO;
        Vector2f input = packet.getMoveVector();
        boolean leftPaddle = packet.getInputData().contains(PlayerAuthInputData.PADDLING_LEFT);
        boolean rightPaddle = packet.getInputData().contains(PlayerAuthInputData.PADDLING_RIGHT);
        double forward = leftPaddle || rightPaddle ? leftPaddle && rightPaddle ? 1 : 0.35
                : input == null ? 0 : clamp(input.getY(), -1, 1);
        double yaw = vehicleYaw(packet);
        double waterDifference = vehicle instanceof EntityBoat boat ? boat.getWaterLevel() : Double.MAX_VALUE;
        for (int tick = 0; tick < ticks; tick++) {
            velocity = velocity.multiply(0.9, 1, 0.9);
            if (leftPaddle != rightPaddle) yaw += leftPaddle ? -0.035 : 0.035;
            velocity = velocity.add(-Math.sin(yaw) * forward * 0.04, 0,
                    Math.cos(yaw) * forward * 0.04);
            if (inWater(vehicle)) {
                if (Double.isFinite(waterDifference) && waterDifference != Double.MAX_VALUE) {
                    double correction = -(waterDifference + movement.y()) * 0.035 - velocity.y() * 0.82;
                    velocity = new Vec3(velocity.x(), clamp(velocity.y() + correction, -0.025, 0.025), velocity.z());
                } else velocity = new Vec3(velocity.x(), clamp(velocity.y() + 0.04, -0.08, 0.08), velocity.z());
                velocity = applyBubbleColumn(vehicle, velocity);
            } else velocity = velocity.add(0, -vehicle.getGravity(), 0);
            movement = movement.add(velocity);
        }
        return new VehicleSimulation(movement, velocity);
    }

    private static VehicleSimulation predictMount(Entity vehicle, PlayerAuthInputPacket packet, Vec3 start,
                                                   int ticks) {
        Vec3 velocity = start;
        Vec3 movement = Vec3.ZERO;
        Vector2f input = packet.getMoveVector();
        double strafe = input == null ? 0 : clamp(input.getX(), -1, 1);
        double forward = input == null ? 0 : clamp(input.getY(), -1, 1);
        double length = Math.hypot(strafe, forward);
        if (length > 1) {
            strafe /= length;
            forward /= length;
        }
        double yaw = Math.toRadians(packet.getInteractRotation() == null
                ? packet.getPlayerRotation().getY() : packet.getInteractRotation().getY());
        double cap = Math.max(0.1, vehicle.getMovementSpeed()) / 1.8;
        if (packet.getInputData().contains(PlayerAuthInputData.SPRINTING)) cap *= vehicle.getSprintMultiplier();
        double targetX = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * cap;
        double targetZ = (Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * cap;

        for (int tick = 0; tick < ticks; tick++) {
            velocity = new Vec3(velocity.x() + (targetX - velocity.x()) * 0.3,
                    velocity.y(), velocity.z() + (targetZ - velocity.z()) * 0.3);
            if (vehicle.isAirControlled()) {
                double vertical = packet.getInputData().contains(PlayerAuthInputData.ASCEND) ? cap
                        : packet.getInputData().contains(PlayerAuthInputData.DESCEND) ? -cap : 0;
                velocity = new Vec3(velocity.x(), velocity.y() + (vertical - velocity.y()) * 0.3, velocity.z());
            } else if (!vehicle.isOnGround()) {
                velocity = velocity.add(0, -vehicle.getGravity(), 0);
            }
            movement = movement.add(velocity);
        }
        return new VehicleSimulation(movement, velocity);
    }

    private static double movementLimitError(Entity vehicle, Vec3 movement, int ticks) {
        double horizontal = movement.horizontalLength() / ticks;
        double vertical = Math.abs(movement.y()) / ticks;
        double horizontalLimit;
        double verticalLimit;
        if (vehicle instanceof EntityBoat) {
            horizontalLimit = Math.max(0.65, Math.hypot(vehicle.motionX, vehicle.motionZ) + 0.2);
            verticalLimit = Math.max(inWater(vehicle) ? 0.18 : 0.65, Math.abs(vehicle.motionY) + 0.2);
        } else {
            horizontalLimit = Math.max(Math.max(0.45, vehicle.getMovementSpeed() * 1.8),
                    Math.hypot(vehicle.motionX, vehicle.motionZ) + 0.25);
            verticalLimit = Math.max(vehicle.isAirControlled()
                    ? Math.max(0.45, vehicle.getMovementSpeed() * 1.8) : 0.75,
                    Math.abs(vehicle.motionY) + 0.25);
        }
        return Math.hypot(Math.max(0, horizontal - horizontalLimit), Math.max(0, vertical - verticalLimit));
    }

    private static CollisionResolver.CollisionResult collide(Entity vehicle, Vec3 requested) {
        Aabb box = Aabb.from(vehicle.getBoundingBox());
        Aabb area = box.stretch(requested).expand(1.0E-4);
        AxisAlignedBB query = new SimpleAxisAlignedBB(area.minX(), area.minY(), area.minZ(),
                area.maxX(), area.maxY(), area.maxZ());
        List<Aabb> boxes = new ArrayList<>();
        for (Block block : vehicle.getLevel().getCollisionBlocks(query, false)) {
            AxisAlignedBB[] collisions = block.getCollisionBoxes();
            if (collisions == null) continue;
            for (AxisAlignedBB collision : collisions) {
                if (collision != null) boxes.add(Aabb.from(collision));
            }
        }
        return CollisionResolver.resolveWithBoxes(box, requested, vehicle.isOnGround(), boxes);
    }

    private static Vec3 vehicleTarget(Entity vehicle, Vector3f packetPosition) {
        double y = packetPosition.getY();
        if (vehicle instanceof EntityBoat boat) y -= boat.getBaseOffset();
        return new Vec3(packetPosition.getX(), y, packetPosition.getZ());
    }

    private static boolean inWater(Entity vehicle) {
        String inside = vehicle.getLevel().getBlock(vehicle.getFloorX(), vehicle.getFloorY(), vehicle.getFloorZ()).getId();
        String below = vehicle.getLevel().getBlock(vehicle.getFloorX(), vehicle.getFloorY() - 1, vehicle.getFloorZ()).getId();
        return inside.contains("water") || below.contains("water");
    }

    private static Vec3 applyBubbleColumn(Entity vehicle, Vec3 velocity) {
        Block block = vehicle.getLevel().getBlock(vehicle.getFloorX(), vehicle.getFloorY(), vehicle.getFloorZ());
        if (!(block instanceof org.powernukkitx.block.BlockBubbleColumn column)) return velocity;
        return column.isDragDown()
                ? new Vec3(velocity.x(), Math.max(-0.3, velocity.y() - 0.03), velocity.z())
                : new Vec3(velocity.x(), Math.min(0.7, velocity.y() + 0.08), velocity.z());
    }

    private static double nearbyEntityAllowance(Entity vehicle, Vec3 movement) {
        Aabb swept = Aabb.from(vehicle.getBoundingBox()).stretch(movement).expand(0.25);
        AxisAlignedBB area = new SimpleAxisAlignedBB(swept.minX(), swept.minY(), swept.minZ(),
                swept.maxX(), swept.maxY(), swept.maxZ());
        return vehicle.getLevel().getNearbyEntities(area, vehicle).length == 0 ? 0 : 0.35;
    }

    private static double vehicleYaw(PlayerAuthInputPacket packet) {
        Vector2f rotation = packet.getVehicleRotation();
        return Math.toRadians(rotation == null ? packet.getPlayerRotation().getY() : rotation.getY());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record VehicleSimulation(Vec3 movement, Vec3 velocity) {
    }
}
