package nay.amethyst.prediction.vehicle;

import nay.amethyst.data.player.PlayerData;
import nay.amethyst.listener.network.support.NetworkCheckSupport;
import nay.amethyst.simulation.movement.FloatBox;
import nay.amethyst.simulation.movement.FloatVector;
import nay.amethyst.simulation.movement.MovementCollisionEngine;
import org.powernukkitx.math.Vector3;
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

        Vector3 start = new Vector3(vehicle.x, vehicle.y, vehicle.z);
        Vector3 target = vehicleTarget(vehicle, packet.getPosition());
        Vector3 requested = target.add(-start.x, -start.y, -start.z);
        Vector3 current = data.predictedVehicleVelocity == null ? Vector3.ZERO : data.predictedVehicleVelocity;
        Vector3 server = new Vector3(vehicle.motionX, vehicle.motionY, vehicle.motionZ);
        Vector3f delta = packet.getPosDelta();
        Vector3 reported = delta == null ? Vector3.ZERO : new Vector3(delta.getX(), delta.getY(), delta.getZ());
        if (reported.lengthSquared() > 9) reported = Vector3.ZERO;
        VehicleSimulation simulation = bestSimulation(vehicle, packet, requested, current, server, reported, ticks);

        MovementCollisionEngine.BoxCollision collision = collide(vehicle, requested);
        FloatVector resolved = collision.movement();
        double collisionError = requested.distance(new Vector3(resolved.x(), resolved.y(), resolved.z()));
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
        Vector3 target = new Vector3(position.getX(), position.getY(), position.getZ());
        Vector3 serverSeat = new Vector3(player.x, player.y + player.getBaseOffset(), player.z);
        Vector3 movement = new Vector3(vehicle.motionX * ticks, vehicle.motionY * ticks, vehicle.motionZ * ticks);
        double latencyTicks = Math.min(2, Math.max(0, NetworkCheckSupport.ping(player) / 50.0));
        double tolerance = (vehicle instanceof EntityMinecartAbstract ? 0.45 : 0.6)
                + Math.hypot(vehicle.motionX, vehicle.motionZ) * latencyTicks;
        double offset = Math.max(0, target.distance(serverSeat) - tolerance);
        return new VehiclePredictionResult(vehicle instanceof EntityMinecartAbstract ? "minecart" : "passive-mount",
                offset, movement, new Vector3(vehicle.motionX, vehicle.motionY, vehicle.motionZ), false, false);
    }

    private static VehicleSimulation bestSimulation(Entity vehicle, PlayerAuthInputPacket packet, Vector3 requested,
                                                     Vector3 predicted, Vector3 server, Vector3 reported, int ticks) {
        List<Vector3> starts = List.of(predicted, server, reported, Vector3.ZERO);
        VehicleSimulation best = null;
        for (Vector3 start : starts) {
            VehicleSimulation candidate = vehicle instanceof EntityBoat
                    ? predictBoat(vehicle, packet, start, ticks)
                    : predictMount(vehicle, packet, start, ticks);
            if (best == null || requested.distance(candidate.movement()) < requested.distance(best.movement())) {
                best = candidate;
            }
        }
        return best;
    }

    private static VehicleSimulation predictBoat(Entity vehicle, PlayerAuthInputPacket packet, Vector3 start,
                                                  int ticks) {
        Vector3 velocity = start;
        Vector3 movement = Vector3.ZERO;
        Vector2f input = packet.getMoveVector();
        boolean leftPaddle = packet.getInputData().contains(PlayerAuthInputData.PADDLING_LEFT);
        boolean rightPaddle = packet.getInputData().contains(PlayerAuthInputData.PADDLING_RIGHT);
        double forward = leftPaddle || rightPaddle ? leftPaddle && rightPaddle ? 1 : 0.35
                : input == null ? 0 : clamp(input.getY(), -1, 1);
        double yaw = vehicleYaw(packet);
        double waterDifference = vehicle instanceof EntityBoat boat ? boat.getWaterLevel() : Double.MAX_VALUE;
        for (int tick = 0; tick < ticks; tick++) {
            velocity = new Vector3(velocity.x * 0.9, velocity.y, velocity.z * 0.9);
            if (leftPaddle != rightPaddle) yaw += leftPaddle ? -0.035 : 0.035;
            velocity = velocity.add(-Math.sin(yaw) * forward * 0.04, 0,
                    Math.cos(yaw) * forward * 0.04);
            if (inWater(vehicle)) {
                if (Double.isFinite(waterDifference) && waterDifference != Double.MAX_VALUE) {
                    double correction = -(waterDifference + movement.y) * 0.035 - velocity.y * 0.82;
                    velocity = new Vector3(velocity.x, clamp(velocity.y + correction, -0.025, 0.025), velocity.z);
                } else velocity = new Vector3(velocity.x, clamp(velocity.y + 0.04, -0.08, 0.08), velocity.z);
                velocity = applyBubbleColumn(vehicle, velocity);
            } else velocity = velocity.add(0, -vehicle.getGravity(), 0);
            movement = movement.add(velocity);
        }
        return new VehicleSimulation(movement, velocity);
    }

    private static VehicleSimulation predictMount(Entity vehicle, PlayerAuthInputPacket packet, Vector3 start,
                                                   int ticks) {
        Vector3 velocity = start;
        Vector3 movement = Vector3.ZERO;
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
            velocity = new Vector3(velocity.x + (targetX - velocity.x) * 0.3,
                    velocity.y, velocity.z + (targetZ - velocity.z) * 0.3);
            if (vehicle.isAirControlled()) {
                double vertical = packet.getInputData().contains(PlayerAuthInputData.ASCEND) ? cap
                        : packet.getInputData().contains(PlayerAuthInputData.DESCEND) ? -cap : 0;
                velocity = new Vector3(velocity.x, velocity.y + (vertical - velocity.y) * 0.3, velocity.z);
            } else if (!vehicle.isOnGround()) {
                velocity = velocity.add(0, -vehicle.getGravity(), 0);
            }
            movement = movement.add(velocity);
        }
        return new VehicleSimulation(movement, velocity);
    }

    private static double movementLimitError(Entity vehicle, Vector3 movement, int ticks) {
        double horizontal = Math.hypot(movement.x, movement.z) / ticks;
        double vertical = Math.abs(movement.y) / ticks;
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

    private static MovementCollisionEngine.BoxCollision collide(Entity vehicle, Vector3 requested) {
        AxisAlignedBB bounds = vehicle.getBoundingBox();
        AxisAlignedBB query = bounds.addCoord(requested.x, requested.y, requested.z)
                .grow(1.0E-4, 1.0E-4, 1.0E-4);
        List<FloatBox> boxes = new ArrayList<>();
        for (Block block : vehicle.getLevel().getCollisionBlocks(query, false)) {
            AxisAlignedBB[] collisions = block.getCollisionBoxes();
            if (collisions == null) continue;
            for (AxisAlignedBB collision : collisions) {
                if (collision != null) boxes.add(floatBox(collision));
            }
        }
        FloatVector movement = new FloatVector((float) requested.x, (float) requested.y, (float) requested.z);
        return MovementCollisionEngine.collide(floatBox(bounds), movement, vehicle.isOnGround(), boxes);
    }

    private static FloatBox floatBox(AxisAlignedBB box) {
        return new FloatBox((float) box.getMinX(), (float) box.getMinY(), (float) box.getMinZ(),
                (float) box.getMaxX(), (float) box.getMaxY(), (float) box.getMaxZ());
    }

    private static Vector3 vehicleTarget(Entity vehicle, Vector3f packetPosition) {
        double y = packetPosition.getY();
        if (vehicle instanceof EntityBoat boat) y -= boat.getBaseOffset();
        return new Vector3(packetPosition.getX(), y, packetPosition.getZ());
    }

    private static boolean inWater(Entity vehicle) {
        String inside = vehicle.getLevel().getBlock(vehicle.getFloorX(), vehicle.getFloorY(), vehicle.getFloorZ()).getId();
        String below = vehicle.getLevel().getBlock(vehicle.getFloorX(), vehicle.getFloorY() - 1, vehicle.getFloorZ()).getId();
        return inside.contains("water") || below.contains("water");
    }

    private static Vector3 applyBubbleColumn(Entity vehicle, Vector3 velocity) {
        Block block = vehicle.getLevel().getBlock(vehicle.getFloorX(), vehicle.getFloorY(), vehicle.getFloorZ());
        if (!(block instanceof org.powernukkitx.block.BlockBubbleColumn column)) return velocity;
        return column.isDragDown()
                ? new Vector3(velocity.x, Math.max(-0.3, velocity.y - 0.03), velocity.z)
                : new Vector3(velocity.x, Math.min(0.7, velocity.y + 0.08), velocity.z);
    }

    private static double nearbyEntityAllowance(Entity vehicle, Vector3 movement) {
        AxisAlignedBB area = vehicle.getBoundingBox().addCoord(movement.x, movement.y, movement.z)
                .grow(0.25, 0.25, 0.25);
        return vehicle.getLevel().getNearbyEntities(area, vehicle).length == 0 ? 0 : 0.35;
    }

    private static double vehicleYaw(PlayerAuthInputPacket packet) {
        Vector2f rotation = packet.getVehicleRotation();
        return Math.toRadians(rotation == null ? packet.getPlayerRotation().getY() : rotation.getY());
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record VehicleSimulation(Vector3 movement, Vector3 velocity) {
    }
}
